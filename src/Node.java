public class Node {
    int id;
    Node next;
    Node shortcut; 

    public Node(int id) {
        this.id = id;
        this.next = null;
        this.shortcut = null;
    }
}
