package node;

import lombok.EqualsAndHashCode;

import java.util.concurrent.PriorityBlockingQueue;

@EqualsAndHashCode
public class Pipe {
    private final PriorityBlockingQueue<Message> priorityQueue; // message의 order가 높은 순서대로 처리하도록.

    public Pipe() {
        priorityQueue = new PriorityBlockingQueue<>();
    }

    public boolean isEmpty() {
        return priorityQueue.isEmpty();
    }

    public Message poll() throws InterruptedException {
        return priorityQueue.take();
    }

    public void push(Message message) {
        priorityQueue.add(message);
    }
}
