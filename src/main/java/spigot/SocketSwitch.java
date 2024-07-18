package spigot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class SocketSwitch
{
    private Thread clientThread;
    private Thread socketThread;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    public String sendmsg;
    public common.Main plugin;
    
	public SocketSwitch(common.Main plugin)
	{
		this.plugin = plugin;
	}
	//Client side
	public void startSocketClient(String sendmsg)
	{
		if(SetConfig.config.getInt("Socket.Client_Port")==0)
		{
			this.plugin.getLogger().info("Client Socket is canceled for config value not given");
			return;
		}
		this.plugin.getLogger().info("Client Socket is Available");
        clientThread = new Thread(() ->
        {
        	
            String hostname = "localhost";
            
            try (Socket socket = new Socket(hostname, SetConfig.config.getInt("Socket.Client_Port"));
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream()))
            {
                // 送信するデータの準備
                ByteArrayDataOutput dataOut = ByteStreams.newDataOutput();
                dataOut.writeUTF(sendmsg); // 例として文字列を送信

                // データの送信
                byte[] dataToSend = dataOut.toByteArray();
                out.writeInt(dataToSend.length); // データの長さを最初に送信
                out.write(dataToSend); // 実際のデータを送信

                // レスポンスの受信
                int length = in.readInt(); // レスポンスの長さを最初に受信
                byte[] responseData = new byte[length];
                in.readFully(responseData); // レスポンスデータを受信

                // 受信したデータの処理
                String response = new String(responseData, "UTF-8");
                this.plugin.getLogger().info("Server response: " + response);

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });

        clientThread.start();
    }

    public void stopSocketClient()
    {
        try
        {
            if (clientThread != null && clientThread.isAlive())
            {
                clientThread.interrupt();
                clientThread.join();
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    //Server side
    public void startSocketServer()
    {
		if(Objects.isNull(SetConfig.config.getInt("Socket.Server_Port")) || SetConfig.config.getInt("Socket.Server_Port") ==0)
		{
			this.plugin.getLogger().info("Socket Server is canceled for config value not given");
			return;
		}
		this.plugin.getLogger().info("Server Socket is Available");
        socketThread = new Thread(() ->
        {
            try
            {
                serverSocket = new ServerSocket(SetConfig.config.getInt("Socket.Server_Port"));
                this.plugin.getLogger().info("Socket Server is listening on port " + SetConfig.config.getInt("Socket.Server_Port"));

                while (running)
                {
                    try
                    {
                        Socket socket = serverSocket.accept();
                        if (!running)
                        {
                            socket.close();
                            break;
                        }
                        this.plugin.getLogger().info("New client connected");
                        new SocketServerThread(socket, plugin).start();
                    }
                    catch (Exception e)
                    {
                        if (running) 
                        {
                            this.plugin.getLogger().severe("Error accepting client connection");
                            e.printStackTrace();
                        }
                    }
                }
            }
            catch (Exception e)
            {
                this.plugin.getLogger().severe("Socket Server socket error");
                e.printStackTrace();
            } 
           finally
           {
                try
                {
                    if (serverSocket != null && !serverSocket.isClosed())
                    {
                        serverSocket.close();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });

        socketThread.start();
    }

    public void stopSocketServer()
    {
        running = false;
        try
        {
            if (serverSocket != null && !serverSocket.isClosed()) 
            {
                serverSocket.close(); // これによりaccept()が解除される
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        try
        {
            if (socketThread != null && socketThread.isAlive()) 
            {
                socketThread.join(1000); // 1秒以内にスレッドの終了を待つ
                if (socketThread.isAlive())
                {
                    socketThread.interrupt(); // 強制的にスレッドを停止
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
