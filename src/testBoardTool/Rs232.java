package testBoardTool;

import java.util.ArrayList;
import java.util.List;

import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import serial.test.SendDataToSerialPortFailure;
import serial.test.SerialPortOutputStreamCloseFailure;
import serial.test.NoSuchPort;
import serial.test.NotASerialPort;
import serial.test.PortInUse;
import serial.test.SerialPortParameterFailure;
import serial.test.TooManyListeners;
import serial.test.ReadDataFromSerialPortFailure;
import serial.test.SerialPortInputStreamCloseFailure;
import serial.test.SerialTool;

public class Rs232 {
	private SerialPort serialPort = null;	//保存串口对象
	private boolean comOpenedFlag = false;	//串口已打开标志位
	private String comName;//获取串口名称
	private int bps = 115200;
	private int parity = SerialPort.PARITY_NONE;
	
	public interface Cp168Listener{
		void gotRecv(byte[] recvbuf);
	};
	//private HopperListener hopperListener;
	List<Cp168Listener> maplist = new ArrayList<Cp168Listener>();
	public void setCp168Listener(Cp168Listener cp168Listener) {
		maplist.add(cp168Listener);
	}
	
	public Rs232(String comNum) {
		comName = comNum;
		//parityStr = SerialPort.PARITY_ODD;
		//parityStr = SerialPort.PARITY_EVEN;
		//parityStr = SerialPort.PARITY_NONE;
		openCloseComPort(true);
	}
	
	/**
	 * 串口发送数据
	 * @param byte[]	sendbuf 发送的数据数组
	 */
	public void ComSendData(byte[] sendbuf) {
		try{
			SerialTool.sendToPort(serialPort, sendbuf);
		} catch(SendDataToSerialPortFailure | SerialPortOutputStreamCloseFailure e4) {
			System.out.println("[ERROR]Cp168-SendDataToSerialPortFailure, SerialPortOutputStreamCloseFailure");
		}
	}

	/**
	 * 打开或关闭Hopper的串口
	 * @param boolean	open_true 如果为true则打开串口，如果为false则关闭串口
	 */
	public void openCloseComPort(boolean open_true) {
		if(open_true) {
			if(comOpenedFlag) {
				return;
			}
		} else {
			if(!comOpenedFlag) {
				return;
			}
		}
		if(open_true) {
			if (comName == null || comName.equals("")) {//检查串口名称是否获取正确
				System.out.println("[ERROR]Hopper-串口号无效");			
			} else {
				try {
					serialPort = SerialTool.openPort(comName, bps, parity, SerialPort.DATABITS_8, SerialPort.STOPBITS_1);//获取指定端口名及波特率的串口对象
					SerialTool.addListener(serialPort, new SerialListener());//在该串口对象上添加监听器
					System.out.println("[ERROR]Cp168-串口打开成功，开始监听");
					comOpenedFlag = true;
				} catch (SerialPortParameterFailure | NotASerialPort | NoSuchPort | PortInUse | TooManyListeners e1) {
					System.out.println("[ERROR]Cp168-串口打开出错");
					comOpenedFlag = false;
					e1.printStackTrace();
				}
			}
		} else {
			SerialTool.closePort(serialPort);
			comOpenedFlag = false;
		}
	}
	
	/*
	 * 处理输入流中获取的数据，产生监听器的触发事件
	 */
	private void proReceive(byte[] recvbuf) {
		for ( Cp168Listener b : maplist) {
			if(b != null) {
				b.gotRecv(recvbuf);
			}
		}
	}
	
	/**
	 * 以内部类形式创建一个串口监听类
	 * @author xuxingxing
	 *
	 */
	private class SerialListener implements SerialPortEventListener {
	    public void serialEvent(SerialPortEvent serialPortEvent) {//处理监控到的串口事件
	        switch (serialPortEvent.getEventType()) {
	            case SerialPortEvent.BI: // 10 通讯中断
	            	System.out.println("[ERROR]CP168-与串口设备通讯中断");
	            	break;
	            case SerialPortEvent.OE: // 7 溢位（溢出）错误
	            case SerialPortEvent.FE: // 9 帧错误
	            case SerialPortEvent.PE: // 8 奇偶校验错误
	            case SerialPortEvent.CD: // 6 载波检测
	            case SerialPortEvent.CTS: // 3 清除待发送数据
	            case SerialPortEvent.DSR: // 4 待发送数据准备好了
	            case SerialPortEvent.RI: // 5 振铃指示
	            case SerialPortEvent.OUTPUT_BUFFER_EMPTY: // 2 输出缓冲区已清空
	            	break;
	            case SerialPortEvent.DATA_AVAILABLE: // 1 串口存在可用数据
	            	//System.out.println("found data");
					byte[] data = null;
					try {
						if (serialPort == null) {
							System.out.println("[ERROR]CP168-串口对象为空！监听失败！");
						}
						else {
							data = SerialTool.readFromPort(serialPort);	//读取数据，存入字节数组
							//自定义解析过程
							if (data == null || data.length < 1) {	//检查数据是否读取正确
								System.out.println("[ERROR]CP168-读取数据过程中未获取到有效数据！请检查设备或程序！");
								//System.exit(0);//------------------------------
							}
							else {
								proReceive(data);
								//receiveTextArea.append(getReceiveData(data));
								//receiveCountField.setText(Integer.toString(Integer.parseInt(receiveCountField.getText()) + data.length));
							}
						}						
					} catch (ReadDataFromSerialPortFailure | SerialPortInputStreamCloseFailure e) {
						System.out.println("[ERROR]CP168-ReadDataFromSerialPortFailure, SerialPortInputStreamCloseFailure");
						//System.exit(0);	//发生读取错误时显示错误信息后退出系统//------------------------------
					}	
					break;
	        }
	    }
	}
}

