package gloom;

/**
 * Contrat commun des backends audio : effets (via {@link Sfx.Sink}) + musique MED.
 *
 * Le desktop l'implémente avec OpenAL ({@code gloom.host.Audio}), Android avec un AudioTrack et
 * son propre mixeur ({@code gloom.android.AndroidAudio}). La vue 3D
 * (projet voisin {@code gloom-rebirth}) ne connaît que cette interface, ce qui lui évite de dépendre
 * de l'une ou l'autre plateforme.
 */
public interface AudioBackend extends Sfx.Sink {

    /** Démarre (ou remplace) la musique depuis un module MED MMD0/MMD1 brut. */
    void playMusic(byte[] module);

    /** À appeler chaque frame : entretient le streaming de la musique. */
    void updateMusic();

    /** Libère les ressources audio. */
    void shutdown();
}
