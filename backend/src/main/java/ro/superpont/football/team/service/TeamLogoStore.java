package ro.superpont.football.team.service;

public interface TeamLogoStore {
    boolean contains(String hash);
    byte[] read(String hash);
    void write(String hash, byte[] png);
}
