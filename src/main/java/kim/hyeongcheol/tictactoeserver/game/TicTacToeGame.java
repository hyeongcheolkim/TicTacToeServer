package kim.hyeongcheol.tictactoeserver.game;

import lombok.Getter;

import java.util.Arrays;
import java.util.Random;

@Getter
public class TicTacToeGame {
    private final Character[] board = new Character[9];
    private final String playerXSessionId;
    private final String playerOSessionId;
    private String currentPlayerSessionId;
    private String winnerSessionId;

    private boolean gameOver;

    private final int[][] winConditions = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    public TicTacToeGame(String player1SessionId, String player2SessionId) {
        if (new Random().nextBoolean()) {
            this.playerXSessionId = player1SessionId;
            this.playerOSessionId = player2SessionId;
        } else {
            this.playerXSessionId = player2SessionId;
            this.playerOSessionId = player1SessionId;
        }
        this.currentPlayerSessionId = this.playerXSessionId;
        Arrays.fill(board, null);
    }

    public boolean makeMove(int index, String sessionId) {
        if (gameOver || board[index] != null || !sessionId.equals(currentPlayerSessionId)) {
            return false;
        }

        board[index] = (sessionId.equals(playerXSessionId)) ? 'X' : 'O';

        if (checkWinner()) {
            this.winnerSessionId = sessionId;
            this.gameOver = true;
        } else if (isBoardFull()) {
            this.gameOver = true;
        } else {
            this.currentPlayerSessionId = (currentPlayerSessionId.equals(playerXSessionId)) ? playerOSessionId : playerXSessionId;
        }
        return true;
    }

    private boolean checkWinner() {
        for (var condition : winConditions) {
            if (board[condition[0]] != null && board[condition[0]] == board[condition[1]] && board[condition[1]] == board[condition[2]]) {
                return true;
            }
        }
        return false;
    }

    private boolean isBoardFull() {
        return Arrays.stream(board).allMatch(cell -> cell != null);
    }
}