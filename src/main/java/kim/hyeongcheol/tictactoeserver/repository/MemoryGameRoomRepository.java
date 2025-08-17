package kim.hyeongcheol.tictactoeserver.repository;

import kim.hyeongcheol.tictactoeserver.domain.GameRoom;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class MemoryGameRoomRepository implements GameRoomRepository{
    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final Map<String, String> userSessionToRoomId = new ConcurrentHashMap<>();

    @Override
    public GameRoom save(GameRoom gameRoom) {
        gameRooms.put(gameRoom.getRoomId(), gameRoom);
        return gameRoom;
    }

    @Override
    public Optional<GameRoom> findById(String roomId) {
        return Optional.ofNullable(gameRooms.get(roomId));
    }

    @Override
    public void deleteById(String roomId) {
        gameRooms.remove(roomId);
    }

    @Override
    public List<GameRoom> findAll() {
        return new ArrayList<>(gameRooms.values());
    }

    @Override
    public void linkSessionToRoom(String sessionId, String roomId) {
        userSessionToRoomId.put(sessionId, roomId);
    }

    @Override
    public Optional<String> findRoomIdBySessionId(String sessionId) {
        return Optional.ofNullable(userSessionToRoomId.get(sessionId));
    }

    @Override
    public void unlinkSessionFromRoom(String sessionId) {
        userSessionToRoomId.remove(sessionId);
    }
}
