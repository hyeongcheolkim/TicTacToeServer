package kim.hyeongcheol.tictactoeserver.model;

import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class GameRoom {
    private final String roomId;
    private final String roomName;
    private final String hostNickname;
    private final String hostSessionId;

    private final Map<String, String> players = new ConcurrentHashMap<>();

    private final Set<String> readyPlayerSessionIds = Collections.synchronizedSet(new HashSet<>());
    private TicTacToeGame game;

    @Setter
    private GameState gameState = GameState.WAITING;

    public enum GameState {
        WAITING,
        PLAYING,
        FINISHED
    }

    public GameRoom(String roomId, String roomName, String hostNickname, String hostSessionId) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostNickname = hostNickname;
        this.hostSessionId = hostSessionId;
        players.put(hostSessionId, hostNickname);
    }

    public void addPlayer(String sessionId, String nickname) {
        if (players.size() < 2) {
            players.put(sessionId, nickname);
        }
    }

    public void removePlayer(String sessionId) {
        players.remove(sessionId);
        readyPlayerSessionIds.clear();
    }

    public void startGame() {
        if (players.size() == 2 && readyPlayerSessionIds.size() == 2) {
            String[] playerSessionIds = players.keySet().toArray(new String[0]);
            this.game = new TicTacToeGame(playerSessionIds[0], playerSessionIds[1]);
            this.gameState = GameState.PLAYING;
            this.readyPlayerSessionIds.clear();
        }
    }

    public void resetForRematch() {
        this.game = null;
        this.gameState = GameState.WAITING;
    }
}