package org.mikhan808.core;

public abstract class UserChat {
    // Base statuses for lobby/menu
    public static final int ENTER_NAME = 1;
    public static final int OK = 2;
    public static final int JOIN_GAME = 11;
    public static final int SELECT_GAME_TYPE = 99;
    private Long id;
    private String name;
    private int status;
    private Game game;

    protected UserChat(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }
}

