package com.brian.wiiuremote;
import android.os.SystemClock;
import java.io.IOException;
import java.net.*;

/** Each start owns a separate cancellation token and UDP socket. */
public final class ControllerClient {
  public interface SocketBinder {
    void bind(DatagramSocket socket) throws IOException;
  }
  private volatile SocketBinder binder = socket -> {};
  public void setSocketBinder(SocketBinder binder) { this.binder = binder; }
  public void networkChanged() {
    Session s = current;
    if (s != null && s.socket != null)
      s.socket.close();
  }
  private static final class Session {
    volatile boolean active = true;
    volatile DatagramSocket socket;
    Thread thread;
  }
  private final InputBuffer input = new InputBuffer();
  private volatile Session current;
  public volatile long sent, replies, lastAck;
  public volatile String error = "";
  public void publish(ControllerPacket.State state) {
    input.publish(state, SystemClock.elapsedRealtime());
  }
  public synchronized void start(String host) {
    stop();
    Session session = new Session();
    current = session;
    sent = replies = lastAck = 0;
    error = "Connecting";
    session.thread = new Thread(() -> run(session, host), "WiiU-UDP");
    session.thread.start();
  }
  public synchronized void stop() {
    Session session = current;
    current = null;
    lastAck = 0;
    if (session == null)
      return;
    session.active = false;
    session.thread.interrupt();
    try {
      session.thread.join(150);
      if (session.thread.isAlive()) {
        if (session.socket != null)
          session.socket.close();
        session.thread.join(150);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    input.discardEdges();
  }
  private void neutral(DatagramSocket socket, ControllerPacket.State config,
                       int sequence) {
    if (socket == null || socket.isClosed())
      return;
    for (int i = 0; i < 3; i++)
      try {
        byte[] b = ControllerPacket.encode(
            ControllerPacket.neutral(config), sequence + i);
        socket.send(new DatagramPacket(b, b.length));
      } catch (IOException ignored) {
        break;
      }
  }
  private void run(Session session, String host) {
    int sequence = 0, backoff = 100;
    ControllerPacket.State config = input.latest();

    while (session.active) {
      DatagramSocket socket = null;
      try {
        socket = new DatagramSocket();
        session.socket = socket;
        if (!host.startsWith("127."))
          binder.bind(socket);
        socket.connect(InetAddress.getByName(host), 26760);
        socket.setSoTimeout(1);
        long openedAt = SystemClock.elapsedRealtime(), ackAt = 0;
        byte[] answer = new byte[32];
        DatagramPacket received = new DatagramPacket(answer, answer.length);
        while (session.active) {
          long frame = SystemClock.elapsedRealtime();
          ControllerPacket.State latest = input.latest();
          if (ControllerPacket.mode(latest) != ControllerPacket.mode(config) || latest.player != config.player) {
            neutral(socket, config, sequence);
            sequence += 3;
            if (current == session)
              lastAck = 0;
            Thread.sleep(350);
            config = ControllerPacket.neutral(latest);
            input.discardEdges();
            openedAt = SystemClock.elapsedRealtime();
            ackAt = 0;
            continue;
          }
          ControllerPacket.State state = input.poll(frame);
          // A concurrent mode change must go through the neutral/disconnect
          // path first.
          if (ControllerPacket.mode(state) != ControllerPacket.mode(config) || state.player != config.player)
            continue;
          byte[] bytes = ControllerPacket.encode(state, sequence++);
          socket.send(new DatagramPacket(bytes, bytes.length));
          if (current == session)
            sent++;
          for (int i = 0; i < 32 && session.active; i++) {
            try {
              received.setLength(answer.length);
              socket.receive(received);
              if (received.getLength() == 4 && answer[0] == 'A' &&
                  answer[1] == 'N' && answer[2] == 'W') {
                if (answer[3] == 'A') {
                  ackAt = SystemClock.elapsedRealtime();
                  backoff = 100;
                  if (current == session) {
                    replies++;
                    lastAck = ackAt;
                    error = "";
                  }
                } else if (answer[3] == 'B') {
                  ackAt = SystemClock.elapsedRealtime();
                  if (current == session) {
                    lastAck = 0;
                    error = "Player " + config.player +
                            (" occupied. Disconnect that controller or " +
                             "choose another player.");
                  }
                }
              }
            } catch (SocketTimeoutException expected) {
              break;
            }
          }
          if (SystemClock.elapsedRealtime() - (ackAt > 0 ? ackAt : openedAt) >
              1800)
            throw new SocketTimeoutException("No Wii U reply; reconnecting");
          long delay = 16 - (SystemClock.elapsedRealtime() - frame);
          if (delay > 0)
            Thread.sleep(delay);
        }
      } catch (InterruptedException expected) {
        session.active = false;
      } catch (Exception e) {
        if (current == session) {
          lastAck = 0;
          error = "Reconnecting: " + (e.getMessage() != null
                                          ? e.getMessage()
                                          : e.getClass().getSimpleName());
        }
      } finally {
        neutral(socket, config, sequence);
        sequence += 3;
        if (socket != null)
          socket.close();
        session.socket = null;
        input.discardEdges();
      }
      if (session.active)
        try {
          Thread.sleep(backoff);
          backoff = Math.min(1500, backoff * 2);
        } catch (InterruptedException expected) {
          session.active = false;
        }
    }
  }
}
