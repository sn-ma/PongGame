package snma.game.pong.event_queue

import java.util.concurrent.PriorityBlockingQueue

// Thread-safe
class EventQueue {
    private val tasksQueue = PriorityBlockingQueue<Task>()

    fun enqueue(time: Long, task: () -> Unit) {
        tasksQueue.add(Task(time, task))
    }

    fun executeIfNeeded() {
        while (true) {
            val peek: Task = tasksQueue.peek() ?: return
            if (peek.time <= System.currentTimeMillis()) {
                tasksQueue.remove(peek)
                peek.action.invoke()
            } else {
                return
            }
        }
    }
}

private class Task(val time: Long, val action: () -> Unit): Comparable<Task> {
    override fun compareTo(other: Task): Int {
        return (time - other.time).toInt()
    }
}