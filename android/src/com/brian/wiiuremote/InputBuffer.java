package com.brian.wiiuremote;
import java.util.ArrayDeque;

/**
 * Retain short press/release edges without replaying a backlog after an
 * outage.
 */
public final class InputBuffer {
  private static final class Edge {
    final ControllerPacket.State state;
    final long time;
    Edge(ControllerPacket.State state, long time) {
      this.state = state;
      this.time = time;
    }
  }
  private ControllerPacket.State latest = new ControllerPacket.State(false);
  private long updatedAt;
  private final ArrayDeque<Edge> edges = new ArrayDeque<>();
  public synchronized void publish(ControllerPacket.State state, long now) {
    ControllerPacket.State next = ControllerPacket.copy(state);
    boolean changed = ControllerPacket.mode(next) != ControllerPacket.mode(latest) || next.player != latest.player;
    if (changed)
      edges.clear();
    if (changed || next.buttons != latest.buttons) {
      if (edges.size() >= 32)
        edges.clear();
      edges.addLast(new Edge(next, now));
    }
    latest = next;
    updatedAt = now;
  }
  public synchronized ControllerPacket.State latest() {
    return ControllerPacket.copy(latest);
  }
  public synchronized ControllerPacket.State poll(long now) {
    if (now - updatedAt > 500) {
      edges.clear();
      return ControllerPacket.neutral(latest);
    }
    while (!edges.isEmpty() && now - edges.peekFirst().time > 120)
      edges.removeFirst();
    ControllerPacket.State outgoing = ControllerPacket.copy(latest);
    if (!edges.isEmpty())
      outgoing.buttons = edges.removeFirst().state.buttons;
    return outgoing;
  }
  public synchronized void discardEdges() { edges.clear(); }
}
