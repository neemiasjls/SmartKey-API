package com.smartkey.config;

import com.smartkey.domain.crypto.ApiKeys;
import com.smartkey.domain.enums.DevicePlatform;
import com.smartkey.domain.model.AccessGrant;
import com.smartkey.domain.model.Credential;
import com.smartkey.domain.model.Device;
import com.smartkey.domain.model.Guest;
import com.smartkey.domain.model.Reader;
import com.smartkey.domain.model.Reservation;
import com.smartkey.repository.CredentialRepository;
import com.smartkey.repository.DeviceRepository;
import com.smartkey.repository.GuestRepository;
import com.smartkey.repository.ReaderRepository;
import com.smartkey.repository.ReservationRepository;
import com.smartkey.service.KeyEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;

/**
 * Cria o cenario de exemplo descrito no projeto, para voce poder testar sem
 * cadastrar tudo na mao.
 *
 *   Hospede : João da Silva
 *   Unidade : apartamento 804
 *   Check-in: ontem as 15:00 (Brasilia)
 *   Checkout: daqui a 3 dias as 11:00 (Brasilia)
 *   Portas  : entrada_condominio, apartamento_804, academia
 *   Leitores: reader_portaria, reader_apto_804, reader_apto_805, reader_academia
 *
 * So roda quando smartkey.seed-demo=true, e apenas se o banco estiver vazio.
 */
@Configuration
@ConditionalOnProperty(name = "smartkey.seed-demo", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private KeyEnrollmentService enrollmentService;

    @Bean
    public ApplicationRunner seedDemoData(GuestRepository guestRepository,
                                          ReservationRepository reservationRepository,
                                          DeviceRepository deviceRepository,
                                          CredentialRepository credentialRepository,
                                          ReaderRepository readerRepository,
                                          KeyEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
        return args -> seed(guestRepository, reservationRepository, deviceRepository,
                credentialRepository, readerRepository);
    }

    @Transactional
    void seed(GuestRepository guestRepository,
              ReservationRepository reservationRepository,
              DeviceRepository deviceRepository,
              CredentialRepository credentialRepository,
              ReaderRepository readerRepository) {

        if (readerRepository.count() > 0 || guestRepository.count() > 0) {
            log.info("Dados de exemplo já existem. Nada a fazer.");
            logExisting(credentialRepository, readerRepository);
            return;
        }

        // ---- Leitores (as fechaduras do predio) --------------------------
        //
        // Cada um recebe a sua PROPRIA chave. No modo demonstracao usamos
        // valores previsiveis (demo_reader_<porta>) so para facilitar o teste;
        // em producao elas sao sorteadas e mostradas uma unica vez.
        List<Reader> readers = List.of(
                new Reader("reader_portaria", "Portaria do condomínio", "entrada_condominio"),
                new Reader("reader_apto_804", "Porta do apartamento 804", "apartamento_804"),
                new Reader("reader_apto_805", "Porta do apartamento 805", "apartamento_805"),
                new Reader("reader_academia", "Porta da academia", "academia"));

        for (Reader reader : readers) {
            reader.setApiKeyHash(ApiKeys.hash(demoKeyFor(reader.getCode())));
        }
        readerRepository.saveAll(readers);

        // ---- Hospede -----------------------------------------------------
        Guest joao = guestRepository.save(
                new Guest("João da Silva", "joao@exemplo.com", "+5511999998888"));

        // ---- Reserva -----------------------------------------------------
        // Relativa a HOJE: check-in ontem às 15h, checkout daqui a 3 dias às
        // 11h. Assim a demonstração sempre abre com uma estadia em andamento,
        // em vez de envelhecer e mostrar tudo como "encerrado".
        LocalDate hoje = LocalDate.now(BRASILIA);
        Instant checkIn = hoje.minusDays(1).atTime(15, 0).atZone(BRASILIA).toInstant();
        Instant checkOut = hoje.plusDays(3).atTime(11, 0).atZone(BRASILIA).toInstant();

        Reservation reserva = reservationRepository.save(
                new Reservation(joao, "804", checkIn, checkOut));

        // ---- Celular do hospede -------------------------------------------
        Device celular = deviceRepository.save(
                new Device(joao, DevicePlatform.ANDROID, "Celular do João"));

        // ---- A chave digital, com as tres permissoes ----------------------
        Credential credencial = new Credential(reserva, celular, checkIn, checkOut);
        credencial.addAccessGrant(new AccessGrant("entrada_condominio"));
        credencial.addAccessGrant(new AccessGrant("apartamento_804"));
        credencial.addAccessGrant(new AccessGrant("academia"));

        // Token de ativacao: e com ele que o celular do hospede registra a
        // sua chave publica, sem precisar da chave de administracao.
        credencial.setEnrollmentToken(enrollmentService.generateToken());

        credencial = credentialRepository.save(credencial);

        log.info("""

                ============================================================
                 DADOS DE EXEMPLO CRIADOS
                ============================================================
                 Hóspede .......: João da Silva
                 Reserva .......: apartamento 804
                 Check-in ......: {}
                 Checkout ......: {}

                 >>> ID DA CREDENCIAL:
                 {}

                 >>> LINK DA CHAVE (abra no celular do hóspede):
                 http://localhost:8080/key.html#{}

                 Leitores, e a chave de cada um:
                   reader_portaria   -> entrada_condominio   demo_reader_reader_portaria
                   reader_apto_804   -> apartamento_804      demo_reader_reader_apto_804
                   reader_apto_805   -> apartamento_805      demo_reader_reader_apto_805
                   reader_academia   -> academia             demo_reader_reader_academia

                 (o 805 NÃO está autorizado para esta credencial - é o teste
                  da porta errada)

                 Painel: http://localhost:8080/
                 API:    http://localhost:8080/swagger-ui.html
                ============================================================
                """, FORMATO.format(checkIn), FORMATO.format(checkOut),
                credencial.getId(), credencial.getEnrollmentToken());

    }

    private void logExisting(CredentialRepository credentialRepository,
                             ReaderRepository readerRepository) {
        credentialRepository.findAll().stream().findFirst().ifPresent(c ->
                log.info("ID de uma credencial existente, para seus testes: {}", c.getId()));
        log.info("Leitores cadastrados: {}",
                readerRepository.findAll().stream().map(Reader::getCode).toList());
    }

    /**
     * Chave previsivel para o modo demonstracao.
     *
     * NUNCA use um esquema assim em producao: quem souber o codigo do leitor
     * saberia a chave dele. Aqui serve so para voce nao precisar copiar
     * quatro chaves aleatorias do console a cada reinicio.
     */
    private static String demoKeyFor(String readerCode) {
        return "demo_reader_" + readerCode;
    }

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm '(Brasília)'").withZone(BRASILIA);
}
