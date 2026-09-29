package com.smartkey.service;

import com.smartkey.domain.crypto.ApiKeys;
import com.smartkey.domain.crypto.SignatureAlgorithm;
import com.smartkey.domain.crypto.SignatureVerifier;
import com.smartkey.domain.enums.AccessGrantStatus;
import com.smartkey.domain.enums.CredentialStatus;
import com.smartkey.domain.enums.ReaderStatus;
import com.smartkey.domain.enums.ReservationStatus;
import com.smartkey.domain.model.AccessGrant;
import com.smartkey.domain.model.Credential;
import com.smartkey.domain.model.Device;
import com.smartkey.domain.model.Guest;
import com.smartkey.domain.model.Reader;
import com.smartkey.domain.model.Reservation;
import com.smartkey.repository.AccessEventRepository;
import com.smartkey.repository.AccessGrantRepository;
import com.smartkey.repository.CredentialRepository;
import com.smartkey.repository.DeviceRepository;
import com.smartkey.repository.GuestRepository;
import com.smartkey.repository.ReaderRepository;
import com.smartkey.repository.ReservationRepository;
import com.smartkey.web.dto.AddGrantRequest;
import com.smartkey.web.dto.CreateDeviceRequest;
import com.smartkey.web.dto.CreateGuestRequest;
import com.smartkey.web.dto.CreateReaderRequest;
import com.smartkey.web.dto.CreateReservationRequest;
import com.smartkey.web.dto.IssueCredentialRequest;
import com.smartkey.web.dto.ReaderWithKeyResponse;
import com.smartkey.web.dto.RegisterPublicKeyRequest;
import com.smartkey.web.error.BadRequestException;
import com.smartkey.web.error.ConflictException;
import com.smartkey.web.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Operacoes do painel administrativo: cadastros, emissao e revogacao. */
@Service
public class AdminService {

    private final GuestRepository guestRepository;
    private final ReservationRepository reservationRepository;
    private final DeviceRepository deviceRepository;
    private final CredentialRepository credentialRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final ReaderRepository readerRepository;
    private final AccessEventRepository accessEventRepository;
    private final KeyEnrollmentService enrollmentService;

    public AdminService(GuestRepository guestRepository,
                        ReservationRepository reservationRepository,
                        DeviceRepository deviceRepository,
                        CredentialRepository credentialRepository,
                        AccessGrantRepository accessGrantRepository,
                        ReaderRepository readerRepository,
                        AccessEventRepository accessEventRepository,
                        KeyEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
        this.guestRepository = guestRepository;
        this.reservationRepository = reservationRepository;
        this.deviceRepository = deviceRepository;
        this.credentialRepository = credentialRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.readerRepository = readerRepository;
        this.accessEventRepository = accessEventRepository;
    }

    // =====================================================================
    // HOSPEDES
    // =====================================================================

    @Transactional
    public Guest createGuest(CreateGuestRequest request) {
        return guestRepository.save(
                new Guest(request.name().trim(), request.email(), request.phone()));
    }

    @Transactional(readOnly = true)
    public List<Guest> listGuests() {
        return guestRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Guest getGuest(UUID id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Hóspede", id));
    }

    // =====================================================================
    // RESERVAS
    // =====================================================================

    @Transactional
    public Reservation createReservation(CreateReservationRequest request) {
        if (!request.checkOutAt().isAfter(request.checkInAt())) {
            throw new BadRequestException(
                    "O checkout precisa ser depois do check-in.");
        }

        Guest guest = getGuest(request.guestId());

        Reservation reservation = new Reservation(
                guest,
                request.unitLabel().trim(),
                request.checkInAt(),
                request.checkOutAt());
        reservation.setNotes(request.notes());

        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<Reservation> listReservations() {
        return reservationRepository.findAllWithGuest();
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(UUID id) {
        return reservationRepository.findByIdWithGuest(id)
                .orElseThrow(() -> NotFoundException.of("Reserva", id));
    }

    /**
     * Cancela a reserva. Todas as credenciais dela param de funcionar
     * imediatamente, sem precisar revogar uma por uma.
     */
    @Transactional
    public Reservation cancelReservation(UUID id) {
        Reservation reservation = getReservation(id);
        reservation.setStatus(ReservationStatus.CANCELLED);
        return reservationRepository.save(reservation);
    }

    // =====================================================================
    // DISPOSITIVOS
    // =====================================================================

    @Transactional
    public Device createDevice(CreateDeviceRequest request) {
        Guest guest = getGuest(request.guestId());
        return deviceRepository.save(
                new Device(guest, request.platform(), request.label()));
    }

    @Transactional(readOnly = true)
    public List<Device> listDevices() {
        return deviceRepository.findAllWithGuest();
    }

    @Transactional(readOnly = true)
    public Device getDevice(UUID id) {
        return deviceRepository.findByIdWithGuest(id)
                .orElseThrow(() -> NotFoundException.of("Dispositivo", id));
    }

    /**
     * Cadastra a chave PUBLICA de um dispositivo.
     *
     * A chave privada correspondente e criada dentro do aparelho e nunca sai
     * de la. Aqui so guardamos a metade publica, que serve exclusivamente
     * para CONFERIR assinaturas - nunca para produzi-las.
     *
     * A chave e validada na hora: se estiver malformada, recusamos agora, em
     * vez de descobrir o problema com o hospede parado na porta.
     */
    @Transactional
    public Device registerPublicKey(UUID deviceId, RegisterPublicKeyRequest request) {
        Device device = getDevice(deviceId);

        SignatureAlgorithm algorithm;
        try {
            algorithm = SignatureAlgorithm.parse(request.algorithm());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        String publicKey = request.publicKey().trim();

        if (!SignatureVerifier.isValidPublicKey(publicKey, algorithm)) {
            throw new BadRequestException(
                    "A chave pública informada não é válida para o algoritmo "
                    + algorithm.name() + ". Envie a chave em Base64, no formato X.509 SPKI.");
        }

        device.setPublicKey(publicKey);
        device.setPublicKeyAlg(algorithm.name());
        return deviceRepository.save(device);
    }

    // =====================================================================
    // LEITORES
    // =====================================================================

    /**
     * Cadastra um leitor e ja sorteia a chave dele.
     *
     * Devolve a chave em texto puro UMA UNICA VEZ. O banco guarda apenas o
     * hash: se a chave for perdida, nao ha como recuperar - so gerar outra.
     * Isso e proposital, e e o que garante que um vazamento do banco nao
     * entregue as chaves das fechaduras.
     */
    @Transactional
    public ReaderWithKeyResponse createReader(CreateReaderRequest request) {
        String code = request.code().trim().toLowerCase();

        if (readerRepository.existsByCode(code)) {
            throw new ConflictException("Já existe um leitor com o código '%s'.".formatted(code));
        }

        Reader reader = new Reader(
                code,
                request.name().trim(),
                request.accessPointCode().trim().toLowerCase());

        String apiKey = ApiKeys.generate();
        reader.setApiKeyHash(ApiKeys.hash(apiKey));

        return ReaderWithKeyResponse.from(readerRepository.save(reader), apiKey);
    }

    /**
     * Troca a chave de um leitor.
     *
     * Use quando um aparelho for perdido, roubado ou substituido: a chave
     * antiga para de funcionar no mesmo instante, e nenhum outro leitor e
     * afetado.
     */
    @Transactional
    public ReaderWithKeyResponse rotateReaderKey(UUID id) {
        Reader reader = readerRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Leitor", id));

        String apiKey = ApiKeys.generate();
        reader.setApiKeyHash(ApiKeys.hash(apiKey));

        return ReaderWithKeyResponse.from(readerRepository.save(reader), apiKey);
    }

    @Transactional(readOnly = true)
    public List<Reader> listReaders() {
        return readerRepository.findAll();
    }

    @Transactional
    public Reader setReaderStatus(UUID id, ReaderStatus status) {
        Reader reader = readerRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Leitor", id));
        reader.setStatus(status);
        return readerRepository.save(reader);
    }

    // =====================================================================
    // CREDENCIAIS (a chave digital)
    // =====================================================================

    /**
     * Emite a chave digital.
     *
     * Por padrao a validade copia o check-in e o checkout da reserva - e e
     * justamente isso que faz a chave "morrer sozinha" depois do checkout,
     * sem precisar de nenhuma rotina agendada.
     */
    @Transactional
    public Credential issueCredential(IssueCredentialRequest request) {
        Reservation reservation = getReservation(request.reservationId());
        Device device = getDevice(request.deviceId());

        // REGRA DE SEGURANCA: o celular tem que pertencer ao hospede da
        // reserva. Sem esta checagem seria possivel emitir a chave do
        // apartamento 804 para o celular de um estranho.
        if (!device.getGuest().getId().equals(reservation.getGuest().getId())) {
            throw new BadRequestException(
                    "Este dispositivo pertence a outro hóspede, diferente do titular da reserva.");
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BadRequestException(
                    "Não é possível emitir credencial para uma reserva cancelada.");
        }

        credentialRepository.findByReservationId(reservation.getId()).stream()
                .filter(c -> c.getDevice().getId().equals(device.getId()))
                .findAny()
                .ifPresent(c -> {
                    throw new ConflictException(
                            "Este dispositivo já possui uma credencial nesta reserva: " + c.getId());
                });

        Credential credential = new Credential(
                reservation,
                device,
                reservation.getCheckInAt(),
                reservation.getCheckOutAt());

        request.accessPointCodes().stream()
                .map(code -> code.trim().toLowerCase())
                .distinct()
                .forEach(code -> credential.addAccessGrant(new AccessGrant(code)));

        // Token de uso unico: e com ele que o celular do hospede registra a
        // sua chave publica, sem precisar da chave de administracao.
        credential.setEnrollmentToken(enrollmentService.generateToken());

        return credentialRepository.save(credential);
    }

    @Transactional(readOnly = true)
    public Credential getCredential(UUID id) {
        return credentialRepository.findByIdWithGrants(id)
                .orElseThrow(() -> NotFoundException.of("Credencial", id));
    }

    @Transactional(readOnly = true)
    public List<Credential> listCredentials() {
        return credentialRepository.findAllWithDetails();
    }

    /** Revoga a chave inteira, antes do checkout. */
    @Transactional
    public Credential revokeCredential(UUID id, String reason) {
        Credential credential = getCredential(id);

        if (credential.getStatus() == CredentialStatus.REVOKED) {
            throw new ConflictException("Esta credencial já está revogada.");
        }

        credential.revoke(reason);
        return credentialRepository.save(credential);
    }

    // =====================================================================
    // PERMISSOES INDIVIDUAIS
    // =====================================================================

    /** Acrescenta uma porta a uma chave ja emitida (ou reativa uma retirada). */
    @Transactional
    public AccessGrant addGrant(UUID credentialId, AddGrantRequest request) {
        Credential credential = getCredential(credentialId);
        String code = request.accessPointCode().trim().toLowerCase();

        AccessGrant existing = accessGrantRepository
                .findByCredentialIdAndAccessPointCode(credentialId, code)
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == AccessGrantStatus.ACTIVE) {
                throw new ConflictException(
                        "Esta credencial já tem permissão ativa para '%s'.".formatted(code));
            }
            existing.reactivate();
            existing.setValidFrom(request.validFrom());
            existing.setValidUntil(request.validUntil());
            return accessGrantRepository.save(existing);
        }

        AccessGrant grant = new AccessGrant(code, request.validFrom(), request.validUntil());
        grant.setCredential(credential);
        return accessGrantRepository.save(grant);
    }

    /** Retira uma porta de uma chave, mantendo o registro para auditoria. */
    @Transactional
    public AccessGrant revokeGrant(UUID credentialId, String accessPointCode) {
        String code = accessPointCode.trim().toLowerCase();

        AccessGrant grant = accessGrantRepository
                .findByCredentialIdAndAccessPointCode(credentialId, code)
                .orElseThrow(() -> new NotFoundException(
                        "Esta credencial não tem permissão para '%s'.".formatted(code)));

        if (grant.getStatus() == AccessGrantStatus.REVOKED) {
            throw new ConflictException("Esta permissão já estava revogada.");
        }

        grant.revoke();
        return accessGrantRepository.save(grant);
    }

    // =====================================================================
    // HISTORICO
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<com.smartkey.domain.model.AccessEvent> listEvents(Pageable pageable) {
        return accessEventRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<com.smartkey.domain.model.AccessEvent> listEventsByCredential(UUID credentialId,
                                                                             Pageable pageable) {
        return accessEventRepository.findByCredentialIdOrderByOccurredAtDesc(credentialId, pageable);
    }
}
