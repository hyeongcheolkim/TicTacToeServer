package kim.hyeongcheol.tictactoeserver.repository;

import kim.hyeongcheol.tictactoeserver.game.GameRoom;

import java.util.List;
import java.util.Optional;

public interface GameRoomRepository {
    GameRoom save(GameRoom gameRoom);
    Optional<GameRoom> findById(String roomId);
    List<GameRoom> findAll();
    void deleteById(String roomId);

    Optional<String> findRoomIdBySessionId(String sessionId);
    void linkSessionToRoom(String sessionId, String roomId);
    void unlinkSessionFromRoom(String sessionId);
}