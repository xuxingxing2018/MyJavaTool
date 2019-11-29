package testBoardTool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;



public class TcpClient {
	private Thread thread;
	private String cp200nIPAddress = "192.168.3.132";
	private int portNumber = 1234;
	private Socket socket;
	private OutputStream outputstream;
	private InputStream inputstream;
	public boolean breakFlag = true;
	public interface Cp200nListener{
		void gotRecv(byte[] recvbuf);
	};
	//private HopperListener hopperListener;
	List<Cp200nListener> maplist = new ArrayList<Cp200nListener>();
	public void setCp200nListener(Cp200nListener cp200nListener) {
		maplist.add(cp200nListener);
	}
	
	/**
	 * 构造方法，建立连接，并准备接收数据
	 */
	public TcpClient() {
		try {
			socket = new Socket(cp200nIPAddress, portNumber);// 与服务端建立连接
			//socket.shutdownOutput();//通过shutdownOutput高速服务器已经发送完数据，后续只能接受数据
			TcpRecvData tcpRecvData = new TcpRecvData();
			thread = new Thread(tcpRecvData);
			thread.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 构造方法，建立连接，并准备接收数据
	 * @param	String 		cp200nipaddress		控制板的IP地址
	 */
	public TcpClient(String cp200nipaddress) {
		this.cp200nIPAddress = cp200nipaddress;
		try {
			socket = new Socket(cp200nIPAddress, portNumber);// 与服务端建立连接
			byte[] resetdat = new byte [3];
			resetdat[0] = 7;resetdat[1] = 7;resetdat[2] = 7;
			TcpSendData(resetdat);
			//socket.shutdownOutput();//通过shutdownOutput高速服务器已经发送完数据，后续只能接受数据
			if(socket != null) {
				TcpRecvData tcpRecvData = new TcpRecvData();
				thread = new Thread(tcpRecvData);
				thread.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 析构方法，关闭连接，释放资源
	 */
	protected void finalize(){
		closeAllConnect();
		System.out.println("close " + cp200nIPAddress + " connect!"); 
	}
	
	/**
	 * TCP发送数据
	 * @param	byte[]	sendbuf		发送数据数组
	 */
	public void TcpSendData(byte[] sendbuf) {
		try {
			outputstream = socket.getOutputStream();// 建立连接后获得输出流
			socket.getOutputStream().write(sendbuf);
			//socket.shutdownOutput();//通过shutdownOutput高速服务器已经发送完数据，后续只能接受数据
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*
	 * 处理输入流中获取的数据，产生监听器的触发事件
	 */
	private void proInputStream(byte[] recvData, int length) {
		for ( Cp200nListener b : maplist) {
			if(b != null) {
				byte[] tmpData = new byte [length];
				for(int i = 0; i < tmpData.length; i++) {
					tmpData[i] = recvData[i];
				}
				b.gotRecv(tmpData);
			}
		}
	}
	
	private class TcpRecvData implements Runnable {
		public TcpRecvData() {
			try {
				inputstream = socket.getInputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void run() {
			try {
				inputstream = socket.getInputStream();
				byte[] bytes = new byte[1024];
			    int len;
			    ByteArrayOutputStream bos = new ByteArrayOutputStream();
			    //StringBuilder sb = new StringBuilder();
			    //while(true) {
			    	//do {
				      	//注意指定编码格式，发送方和接收方一定要统一，建议使用UTF-8
			    		//len = inputstream.read(bytes);
	                	//bos.write(bytes, 0, len);
	                	//proInputStream(bytes, len);
				    //} while(inputstream.available() != 0);
			    	while(breakFlag) {
			    		if(inputstream.available() != 0) {
			    			len = inputstream.read(bytes);
			    			proInputStream(bytes, len);
			    		}
			    	}
			    	/*while ((len = inputstream.read(bytes)) != -1) {
				    //while ((!thread.isInterrupted() || inputstream.available() != 0) && (len = inputstream.read(bytes)) != -1) {
				      //注意指定编码格式，发送方和接收方一定要统一，建议使用UTF-8
				      //sb.append(new String(bytes, 0, len,"UTF-8"));
			    		proInputStream(bytes, len);
				    }*/
				    System.out.println(cp200nIPAddress + " got message: " + bytes);
				    if(!breakFlag) {
				    	socket.close();//释放连接
				    }
			    //}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * 关闭所有连接
	 */
	public void closeAllConnect() {
		try {
			/*if(inputstream != null) {
				inputstream.close();//关闭输入流
			}
			if(outputstream != null) {
				outputstream.close();//关闭输出流
			}*/
			
			if(thread != null) {
				//停止线程的运行
				breakFlag = false;
				thread.interrupt();
				thread = null;
			}
			/*if(socket != null) {
				socket.close();//释放连接
			}*/
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
