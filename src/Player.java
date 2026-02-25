import java.awt.Color;

public class Player {
    private String name;
    private Node currentPosition;
    private Color color;
    private int lastPositionId;
    private int score; 

    public Player(String name, Node startNode, Color color) {
        this.name = name;
        this.currentPosition = startNode;
        this.color = color;
        this.lastPositionId = startNode.id;
        this.score = 0; 
    }

    public String getName() { return name; }
    public Node getCurrentPosition() { return currentPosition; }
    public Color getColor() { return color; }

    public int getLastPositionId() { return lastPositionId; }
    public void setLastPositionId(int id) { this.lastPositionId = id; }

    public int getScore() { return score; }

    public void addScore(int points) {
        this.score += points;
    }

    public void setPosition(Node newNode) {
        this.currentPosition = newNode;
    }

    public void stepForward() {
        if (this.currentPosition.next != null) {
            this.currentPosition = this.currentPosition.next;
        }
    }
}
