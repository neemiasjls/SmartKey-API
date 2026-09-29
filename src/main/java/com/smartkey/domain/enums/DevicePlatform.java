package com.smartkey.domain.enums;

/** Tipo de dispositivo que carrega a chave digital. */
public enum DevicePlatform {
    ANDROID,
    IOS,
    /** Navegador (caso o canal escolhido seja QR Code em vez de NFC). */
    WEB,
    /** Usado apenas em testes automatizados. */
    SIMULATOR
}
