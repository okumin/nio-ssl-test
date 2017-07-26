package niossltest;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class HandshakeLogging {
  private static String PASSWORD = "12345678";

  private static class Buffers {
    ByteBuffer inboundNetworkBuffer = ByteBuffer.allocate(1024 * 1024);
    ByteBuffer outboundNetworkBuffer = ByteBuffer.allocate(1024 * 1024);
  }

  public static void main(String[] args) throws Exception {
    InetSocketAddress serverAddress = new InetSocketAddress("127.0.0.1", 24224);
    ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.configureBlocking(false);
    serverSocketChannel.bind(serverAddress);

    Buffers clientBuffers = new Buffers();
    SocketChannel clientChannel = SocketChannel.open();
    clientChannel.configureBlocking(false);
    clientChannel.connect(serverAddress);

    // TCP Handshake
    Buffers serverBuffers = new Buffers();
    SocketChannel serverChannel = serverSocketChannel.accept();
    serverChannel.configureBlocking(false);
    System.out.println("Accept from " + serverChannel.getRemoteAddress());
    while (!clientChannel.finishConnect()) {
      // Wait until handshake finishes
      // This code should not be executed in production!
      // If production, let Selector notify that the connection becomes established
    }

    SSLContext clientContext = SSLContext.getInstance("TLS");
    clientContext.init(null, createTrustManagers(), null);
    SSLEngine clientEngine = clientContext.createSSLEngine();
    clientEngine.setUseClientMode(true);
    clientEngine.setNeedClientAuth(false);
    clientEngine.setWantClientAuth(false);
    clientEngine.beginHandshake();

    SSLContext serverContext = SSLContext.getInstance("TLS");
    serverContext.init(createKeyManagers(), createTrustManagers(), null);
    SSLEngine serverEngine = serverContext.createSSLEngine();
    serverEngine.setUseClientMode(false);
    serverEngine.setNeedClientAuth(false);
    clientEngine.setWantClientAuth(false);
    serverEngine.beginHandshake();

    // Sends ClientHello
    System.out.println("Sends ClientHello");
    // ClientHello 送信。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_WRAP
    // EngineResult: Status = OK HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 0 bytesProduced = 200
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=200 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(clientChannel, clientEngine, clientBuffers);
    // ClientHello を送信し終えたので、レスポンスを待つモードに。
    // ただしまだサーバからレスポンスを送っていないので、
    // 読み取るパケットが存在せず SocketChannel#read が即座に0を返す。
    // レスポンスを読まずにすぐ呼び出しが終了するのは、
    // configureBlocking(false) を設定し O_NONBLOCKING となっているから。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // EngineResult: Status = BUFFER_UNDERFLOW HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 0 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=0 lim=0 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(clientChannel, clientEngine, clientBuffers);

    // Receives ClientHello and sends responses
    System.out.println("Receives ClientHello");
    // 多分 ClientHello を受け取っている。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=200 lim=1048576 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NEED_TASK
    // bytesConsumed = 200 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=200 lim=200 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_TASK
    handshake(serverChannel, serverEngine, serverBuffers);
    // なんらかの TASK を実行。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_TASK
    // HandshakeStatus(end): NEED_WRAP
    handshake(serverChannel, serverEngine, serverBuffers);
    // ServerHello, ServerCertificate, ServerKeyExchange, CertificateRequest, ServerHelloDone 送信
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_WRAP
    // EngineResult: Status = OK HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 0 bytesProduced = 3217
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=3217 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(serverChannel, serverEngine, serverBuffers);
    // レスポンスをすべて返したので、クライアントからの送信待ち。
    // まだクライアントが返信してないので何もしない。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // EngineResult: Status = BUFFER_UNDERFLOW HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 0 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=0 lim=0 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(serverChannel, serverEngine, serverBuffers);

    // Receives server's responses and sends client's responses
    System.out.println("Receives ServerHelloDone");
    // サーバからのレスポンスを読み込む。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=3217 lim=1048576 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NEED_TASK
    // bytesConsumed = 3217 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=3217 lim=3217 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_TASK
    handshake(clientChannel, clientEngine, clientBuffers);
    // 何らかのタスクを実行。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_TASK
    // HandshakeStatus(end): NEED_WRAP
    handshake(clientChannel, clientEngine, clientBuffers);
    // 何かを送信
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_WRAP
    // EngineResult: Status = OK HandshakeStatus = NEED_WRAP
    // bytesConsumed = 0 bytesProduced = 75
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=75 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_WRAP
    handshake(clientChannel, clientEngine, clientBuffers);
    // さらに何かを送信
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_WRAP
    // EngineResult: Status = OK HandshakeStatus = NEED_WRAP
    // bytesConsumed = 0 bytesProduced = 6
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=6 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_WRAP
    handshake(clientChannel, clientEngine, clientBuffers);
    // さらに何かを送信
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_WRAP
    // EngineResult: Status = OK HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 0 bytesProduced = 101
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=101 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(clientChannel, clientEngine, clientBuffers);
    // クライアントからの仕事はすべて終えたのでサーバを待つモードに。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // EngineResult: Status = BUFFER_UNDERFLOW HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 0 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=0 lim=0 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(clientChannel, clientEngine, clientBuffers);

    // Completes a handshake
    System.out.println("Completes handshake on server-side");
    // クライアントからのレスポンスを受信。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=182 lim=1048576 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NEED_TASK
    // bytesConsumed = 75 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=75 lim=182 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_TASK
    handshake(serverChannel, serverEngine, serverBuffers);
    // 何らかのタスクを実行
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_TASK
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(serverChannel, serverEngine, serverBuffers);
    // 何かを読み込む。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=75 lim=182 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 6 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=81 lim=182 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(serverChannel, serverEngine, serverBuffers);
    // さらに何かを読み込む。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=81 lim=182 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NEED_WRAP
    // bytesConsumed = 101 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=182 lim=182 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_WRAP
    handshake(serverChannel, serverEngine, serverBuffers);
    // 何かを送る。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_WRAP
    // EngineResult: Status = OK HandshakeStatus = NEED_WRAP
    // bytesConsumed = 0 bytesProduced = 6
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=6 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_WRAP
    handshake(serverChannel, serverEngine, serverBuffers);
    // さらに何かを送り、ハンドシェイク完了。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_WRAP
    // EngineResult: Status = OK HandshakeStatus = FINISHED
    // bytesConsumed = 0 bytesProduced = 101
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=101 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NOT_HANDSHAKING
    handshake(serverChannel, serverEngine, serverBuffers);

    System.out.println("Completes handshake on client-side");
    // 何かを読み込む。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=107 lim=1048576 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NEED_UNWRAP
    // bytesConsumed = 6 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=6 lim=107 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NEED_UNWRAP
    handshake(clientChannel, clientEngine, clientBuffers);
    // さらに何かを読み込んでハンドシェイク完了。
    // ----------------------------------------------------
    // HandshakeStatus(start): NEED_UNWRAP
    // ReceivedNetworkBuffer: java.nio.HeapByteBuffer[pos=6 lim=107 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = FINISHED
    // bytesConsumed = 101 bytesProduced = 0
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=107 lim=107 cap=1048576]
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=1048576 cap=1048576]
    // HandshakeStatus(end): NOT_HANDSHAKING
    handshake(clientChannel, clientEngine, clientBuffers);

    System.out.println("After handshake");
    // ハンドシェイク終了したので、何も起きない。
    // ----------------------------------------------------
    // HandshakeStatus(start): NOT_HANDSHAKING
    // HandshakeStatus(end): NOT_HANDSHAKING
    handshake(clientChannel, clientEngine, clientBuffers);
    // サーバ側も同じ。
    // ----------------------------------------------------
    // HandshakeStatus(start): NOT_HANDSHAKING
    // HandshakeStatus(end): NOT_HANDSHAKING
    handshake(serverChannel, serverEngine, serverBuffers);

    // Exchanges application data
    System.out.println("Exchanges application data");
    // データ送信。
    // ----------------------------------------------------
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=0 lim=5 cap=1048576]
    // NetworkBuffer: java.nio.HeapByteBuffer[pos=85 lim=1048576 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NOT_HANDSHAKING
    // bytesConsumed = 5 bytesProduced = 85
    write(clientChannel, clientEngine);
    // データ受信
    // ----------------------------------------------------
    // ApplicationBuffer: java.nio.HeapByteBuffer[pos=5 lim=1048576 cap=1048576]
    // EngineResult: Status = OK HandshakeStatus = NOT_HANDSHAKING
    // bytesConsumed = 85 bytesProduced = 5
    // Received: hello
    read(serverChannel, serverEngine);

    clientChannel.close();
    serverChannel.close();
    serverSocketChannel.close();
  }

  private static void handshake(SocketChannel socketChannel,
                                SSLEngine engine,
                                Buffers buffers) throws Exception {
    // 転送中のパケットが送受信し終えるのに十分な時間待つ
    Thread.sleep(500);
    SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
    System.out.println("HandshakeStatus(start): " + handshakeStatus);

    // never used at handshake phase
    ByteBuffer applicationBuffer = ByteBuffer.allocate(1024 * 1024);

    switch (handshakeStatus) {
      case NEED_WRAP:
        buffers.outboundNetworkBuffer.clear();
        SSLEngineResult wrapEngineResult = engine.wrap(applicationBuffer,
            buffers.outboundNetworkBuffer);
        System.out.println("EngineResult: " + wrapEngineResult);
        System.out.println("ApplicationBuffer: " + applicationBuffer);
        System.out.println("NetworkBuffer: " + buffers.outboundNetworkBuffer);

        buffers.outboundNetworkBuffer.flip();
        while (buffers.outboundNetworkBuffer.hasRemaining()) {
          // Write all the network data
          // This code should not be executed in production!
          // If production, let Selector notify that the socketChannel becomes writable
          socketChannel.write(buffers.outboundNetworkBuffer);
        }
        break;
      case NEED_UNWRAP:
        if (buffers.inboundNetworkBuffer.position() != 0
            && buffers.inboundNetworkBuffer.hasRemaining()) {
          // 前回の NEED_UNWRAP で全メッセージを消化しきれなかったケース
          // ちょっと雑
          System.out.println("ReceivedNetworkBuffer: " + buffers.inboundNetworkBuffer);
          SSLEngineResult unwrapEngineResult = engine.unwrap(buffers.inboundNetworkBuffer,
              applicationBuffer);
          System.out.println("EngineResult: " + unwrapEngineResult);
          System.out.println("NetworkBuffer: " + buffers.inboundNetworkBuffer);
          System.out.println("ApplicationBuffer: " + applicationBuffer);
        } else {
          buffers.inboundNetworkBuffer.clear();
          while (socketChannel.read(buffers.inboundNetworkBuffer) > 0) {
            // Read all the data that the other peer has sent
            // If production, let Selector notify that the socketChannel becomes readable
          }
          System.out.println("ReceivedNetworkBuffer: " + buffers.inboundNetworkBuffer);
          buffers.inboundNetworkBuffer.flip();
          SSLEngineResult unwrapEngineResult = engine.unwrap(buffers.inboundNetworkBuffer,
              applicationBuffer);
          System.out.println("EngineResult: " + unwrapEngineResult);
          System.out.println("NetworkBuffer: " + buffers.inboundNetworkBuffer);
          System.out.println("ApplicationBuffer: " + applicationBuffer);
        }
        break;
      case NEED_TASK:
        Runnable task = engine.getDelegatedTask();
        task.run();
        break;
    }

    // ハンドシェイクフェーズではアプリケーションバッファは使われない。
    if (applicationBuffer.position() != 0) {
      throw new AssertionError();
    }

    System.out.println("HandshakeStatus(end): " + engine.getHandshakeStatus());
    System.out.println();
  }

  private static void write(SocketChannel channel, SSLEngine engine) throws Exception {
    Thread.sleep(500);
    ByteBuffer networkBuffer = ByteBuffer.allocate(1024 * 1024);
    ByteBuffer applicationBuffer = ByteBuffer.allocate(1024 * 1024);

    applicationBuffer.put("hello".getBytes());
    applicationBuffer.flip();
    System.out.println("ApplicationBuffer: " + applicationBuffer);

    SSLEngineResult engineResult = engine.wrap(applicationBuffer, networkBuffer);
    System.out.println("NetworkBuffer: " + networkBuffer);
    System.out.println("EngineResult: " + engineResult);

    networkBuffer.flip();
    while (networkBuffer.hasRemaining()) {
      channel.write(networkBuffer);
    }
    System.out.println();
  }

  private static void read(SocketChannel channel, SSLEngine engine) throws Exception {
    Thread.sleep(500);
    ByteBuffer networkBuffer = ByteBuffer.allocate(1024 * 1024);
    ByteBuffer applicationBuffer = ByteBuffer.allocate(1024 * 1024);

    while (channel.read(networkBuffer) > 0) {
      // 全部読み込む
    }
    System.out.println("ReceivedNetworkBuffer: " + networkBuffer);

    networkBuffer.flip();
    SSLEngineResult engineResult = engine.unwrap(networkBuffer, applicationBuffer);
    System.out.println("ApplicationBuffer: " + applicationBuffer);
    System.out.println("EngineResult: " + engineResult);

    applicationBuffer.flip();
    String message = new String(applicationBuffer.array(), 0, applicationBuffer.limit());
    System.out.println("Received: " + message);
    System.out.println();
  }

  private static KeyManager[] createKeyManagers() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("JKS");
    InputStream inputStream = new FileInputStream("./src/main/resources/nio-ssl-test-server.jks");
    keyStore.load(inputStream, PASSWORD.toCharArray());
    KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore, PASSWORD.toCharArray());
    return factory.getKeyManagers();
  }

  private static TrustManager[] createTrustManagers() throws Exception {
    KeyStore trustStore = KeyStore.getInstance("JKS");
    InputStream inputStream = new FileInputStream("./src/main/resources/nio-ssl-test-ca.jks");
    trustStore.load(inputStream, PASSWORD.toCharArray());
    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(trustStore);
    return factory.getTrustManagers();
  }
}
