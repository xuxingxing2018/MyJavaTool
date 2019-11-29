package testBoardTool;


import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import testBoardTool.Rs232.Cp168Listener;
import testBoardTool.TcpClient.Cp200nListener;



public class Board {
	private static final int BUF_LENGTH = 65536;
	private byte [] RecvDataBuf = new byte [BUF_LENGTH];//接收数据缓冲区
	private byte [] SendDataBuf = new byte [BUF_LENGTH];//发送数据缓冲区
	private int RecvAddIndex;						//数据增加指针
	private int RecvDoneIndex;						//数据处理指针
	private int SendAddIndex;						//数据增加指针
	private byte RecvFc1;							//接收协议的功能码1
	private byte RecvFc2;							//接收协议的功能码2
	private byte RecvCrc;							//接收协议的校验码
	private byte SendCrc;							//发送协议的校验码
	private final byte RECV_HEAD = 3;				//接收协议的协议头
	private final byte RECV_END = 4;				//接收协议的协议尾
	private final byte RECV_ETX = 14;				//接收协议的数据结束符
	
	private final byte SEND_HEAD = 1;				//发送协议的协议头
	private final byte SEND_END = 2;				//发送协议的协议尾
	private final byte SEND_ETX = 14;				//发送协议的数据结束符
	private TcpClient tcpClient;
	private Rs232 rs232;
	private boolean isTcpFlag;						//
	private JScrollPane scrollPane;					//与message 对应的滚动条
	
	private String RecvDataString;					//解析完成后的字符串结果
	private JTextArea RecvMessageArea;
	private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5', 
            '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	
	
	
	public Board(boolean isTcpFlag, String ipAddress_ComNum, 
			JTextArea recvMessageArea, JScrollPane scrollPane) {//构造函数
		RecvAddIndex = 0;							//清空指针
		RecvDoneIndex = 0;							//清空指针
		SendAddIndex = 0;							//清空指针
		this.isTcpFlag = isTcpFlag;
		RecvMessageArea = recvMessageArea;
		this.scrollPane = scrollPane;
		if(isTcpFlag) {//通过TCP方式连接设备
			tcpClient = new TcpClient(ipAddress_ComNum);
			tcpClient.setCp200nListener(new Cp200nListener() {//监听TCP接收并处理
				public void gotRecv(byte[] recvbuf) {
					Recv(recvbuf);//处理接收的数据
				}
			});
		} else {//通过COM方式连接设备
			rs232 = new Rs232(ipAddress_ComNum);
			rs232.setCp168Listener(new Cp168Listener() {//监听串口接收并处理
				public void gotRecv(byte[] recvbuf) {
					Recv(recvbuf);//处理接收的数据
				}
			});
		}
	}
	
	/**
	 * 关闭连接
	 */
	public void disconnect() {
		if(isTcpFlag) {//通过TCP方式连接设备
			tcpClient.closeAllConnect();
		} else {
			rs232.openCloseComPort(false);
		}
	}
	
	/**
	 * 校对时钟，无显示屏显示和语音提示
	 */
	public void setNowTime() {
		getSendDataHead((byte)0x41, (byte)0x30);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");//设置日期格式
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
		if (w < 0) w = 0;
		String[] weekDays = { "00", "01", "02", "03", "04", "05", "06" };
		String nowTime = df.format(new Date()) + weekDays[w];
        //System.out.println(nowTime);
		byte[] myByte = nowTime.getBytes();
		for(int i = 0; i < myByte.length; i++) {
			getSendData1Byte(myByte[i]);
		}
		getSendDataEnd();
		send();
	}

	/**
	 * 设置控制板模式和屏参
	 * @param byte mode_Data[0] 控制板模式，=0x30为普通模式；=0x31为加一体摄像机模式；=0x32为模仿621模式；=0x33为脱机收费模式；=0x3f为不更改
	 * @param byte mode_Data[1] 摄像机类型，=0x30-7098摄像机；=0x31-1类摄像机；=0x32-7182摄像机；=0x33-7183摄像机；
	 * 		=0x34-4类摄像机；=0x35-5类摄像机；=0x36-6类摄像机；=0x37-7类摄像机；=0x38-8类摄像机；=0x39-9类摄像机；=0x3f为不更改
	 * @param byte mode_Data[2] 扫描方式，=0x30为16扫方式0；=0x31为16扫方式1(未实现)；=0x32为8扫方式0；=0x33为8扫方式1(未实现)；
	 * 		=0x34为4扫方式0；=0x35为4扫方式1(未实现)；=0x3f为不更改
	 * @param byte mode_Data[3] 屏幕高度，=0x30为一行汉字；=0x31为二行汉字；=0x32为三行汉字；=0x33为四行汉字；=0x3f为不更改
	 * @param byte mode_Data[4] 屏幕宽度，=0x30为4汉字宽度；=0x31为5汉字宽度(未实现)；=0x32为8汉字宽度；=0x3f为不更改
	 * @param byte mode_Data[5] OE极性，=0x30为正极性；=0x31为负极性；=0x3f为不更改
	 * @param byte mode_Data[6] 数据极性，=0x30为正极性；=0x31为负极性；=0x3f为不更改
	 * @param byte mode_Data[7] 色彩类型，=0x30为单色；=0x31为双基色；=0x32为全彩(未实现)；=0x3f为不更改
	 */
	public void setBoardMode(byte [] mode_Data) {
		if(mode_Data.length != 8) {return;}
		getSendDataHead((byte)0x41, (byte)0x31);
		for(int i = 0; i < mode_Data.length; i++) {
			getSendData1Byte(mode_Data[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取控制板模式和屏参
	 */
	public void readBoardMode() {
		getSendDataHead((byte)0x41, (byte)0x32);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 下载字库文件，载入的文件大小为256KByte，它将被分成4096段，每段64Byte，每段都按顺序编上序号。序号为0x0000-0x0FFF此命令应当连续执行4096次，才能传完整个字库文件。
	 * @param int	doc_index 文件数据指针，取值范围：0-4095
	 * @param byte[] file_64byte_data 文件数据，大小：64字节
	 */
	public void downloadFontLib(int doc_index, byte[] file_64byte_data) {
		if(file_64byte_data.length != 64) {return;}
		if((doc_index >= 4096) || (doc_index < 0)) {return;}
		getSendDataHead((byte)0x41, (byte)0x33);
		byte dat = (byte)(doc_index >>> 8);
		getSendDataUnboxing(dat);
		dat = (byte)(doc_index);
		getSendDataUnboxing(dat);
		for(int i = 0; i < file_64byte_data.length; i++) {
			getSendDataUnboxing(file_64byte_data[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 设置其他参数
	 * @param boolean param_flag[0] 语音播报车牌号码,=true=0x30不播报车牌；=false=0x31播报车牌
	 * @param boolean param_flag[1] 3号继电器自动控制红绿灯,=true=0x30自动控制红绿灯，有效亮绿灯，无效亮红灯；=false=0x31继电器由软件控制
	 * @param boolean param_flag[2] 满位接口透明转发上位机命令, =true=0x30需要转发；=false=0x31不转发
	 * @param boolean param_flag[3] 区分AB接口读卡数据,=true=0x30区分；=false=0x31不区分
	 * @param boolean param_flag[4] 竖屏标志,=true=0x30为竖屏；=false=0x31为正屏
	 * @param boolean param_flag[5] 颠倒标志,=true=0x30为整屏颠倒；=false=0x31为不颠倒
	 * @param boolean param_flag[6] 扫描枪串口接卡机标志,=true=0x30则串口接扫描枪，=false=0x31则串口接卡机
	 */
	public void setOtherParam(boolean [] param_flag) {
		if(param_flag.length != 7) {return;}
		getSendDataHead((byte)0x41, (byte)0x34);
		for(int i = 0; i < param_flag.length; i++) {
			if(param_flag[i]) {
				getSendData1Byte((byte)0x30);
			} else {
				getSendData1Byte((byte)0x31);
			}
			if((i == 1) || (i == 3) || (i == 5)) {
				getSendData1Byte((byte)0x40);
			}
		}
		getSendData1Byte((byte)0x41);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取其他参数
	 */
	public void readOtherParam() {
		getSendDataHead((byte)0x41, (byte)0x37);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 下载自动补光时间，无显示屏显示和语音提示
	 * @param	String lightOnTime 自动开灯时间，取值范围0000-2359，例：0730即早上07:30
	 * @param	String lightOffTime 自动关灯时间，取值范围0000-2359，例：1945即晚上19:45
	 */
	public void setLightTime(String lightOnTime, String lightOffTime) {
		if((lightOnTime.length() != 4) || (lightOffTime.length() != 4)) return;
		getSendDataHead((byte)0x41, (byte)0x35);
		try {
			byte[] myByte;
			myByte = lightOnTime.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
			myByte = lightOffTime.getBytes("GB2312");
			i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 下载音频文件，备注：音源文件为bin文件。这个文件中包含2个部分的内容。第一部分为语音段落的指针，第二部分为语音内容。第一部分为8*1024字节，可以存储（（8*1024）÷6）段的指针，
	 * 每段指针为3字节头指针，3字节尾指针。第二部分为<=16*1024*1024字节。而且bin文件的总长度为64*N字节，方便下载。建议：单独编辑一个音源处理软件。将音源的大量WAV文件另存为
	 * 一个bin文件。然后使用测试软件下载该bin文件。
	 * 载入的文件大小不固定为多少字节，但是一定是可以被64字节整除的。文件大小为N*64Byte。它将被分成N段，每段64Byte，每段都按顺序 编上序号。序号为0x000000-0x040000此命令
	 * 应当连续执行N次，才能传完整个字库文件。文件内容序号(6Byte)=0x3? 0x3? 0x3? 0x3? 0x3? 0x3?则序号为??????，对应内容(128Byte)=64字节的内容拆分为128字节
	 * @param int doc_index 内容序号指针，取值范围0x000000-0x040000
	 * @param byte[] file_64byte_data 文件数据，大小：64字节
	 */
	public void downloadMusicFile(int doc_index, byte[] file_64byte_data) {
		if((doc_index < 0) || (doc_index > 0x040000)) {return;}
		if(file_64byte_data.length != 64) {return;}
		getSendDataHead((byte)0x41, (byte)(0x36));
		byte dat = (byte)(doc_index >>> 16);
		getSendDataUnboxing(dat);
		dat = (byte)(doc_index >>> 8);
		getSendDataUnboxing(dat);
		dat = (byte)(doc_index);
		getSendDataUnboxing(dat);
		for(int i = 0; i < file_64byte_data.length; i++) {
			getSendDataUnboxing(file_64byte_data[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 下载公司名称电话，有显示屏显示，无语音提示
	 * @param	int 	disp_Num 屏的行号，取值1-4，例=4则为显示屏第4行
	 * @param	String 	disp_Data 显示内容
	 */
	public void setCompanyName(int disp_Num, String disp_Data) {
		if((disp_Num < 1) || (disp_Num > 4)) return;
		if(disp_Data.length() > 100) return;
		getSendDataHead((byte)0x42, (byte)(0x30 + disp_Num - 1));
		try {
			byte[] myByte;
			myByte = disp_Data.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 下载纸票打印内容，无显示屏显示和语音提示
	 * @param	int hang 行号，取值范围0-15
	 * @param	int ziTi 字体放大，取值范围0-3
	 * @param	int duiQi 对齐方式，取值范围0-2
	 * @param	String printData 打印内容
	 */
	public void setPrintData(int hang, int ziTi, int duiQi, String printData) {
		if((hang < 0) || (hang > 15)) {return;}
		if((ziTi < 0) || (ziTi > 3)) {return;}
		if((duiQi < 0) || (duiQi > 2)) {return;}
		getSendDataHead((byte)0x42, (byte)0x35);
		getSendData1Byte((byte)(0x30 + hang));
		getSendData1Byte((byte)(0x30 + ziTi));
		getSendData1Byte((byte)(0x30 + duiQi));
		try {
			byte[] myByte;
			myByte = printData.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取纸票打印内容，无显示屏显示和语音提示
	 * @param	int control 控制板自动出票标志位，取值范围0-15
	 */
	public void readPrintData(int control) {
		if((control < 0) || (control > 15)) return;
		getSendDataHead((byte)0x42, (byte)0x3D);
		getSendData1Byte((byte)(0x30 + control));
		getSendDataEnd();
		send();
	}
	
	/**
	 * 下载控制板自动出票标志，无显示屏显示和语音提示
	 * @param	int control 控制板自动出票标志位，取值范围0-3
	 * 						=0x30允许控制板自动出票(写入flash)；
	 * 						=0x31禁止控制板自动出票(写入flash)；
	 * 						=0x32不改变标志位状态，但是通知控制板收到入场票号，要求控制板停止上传票号；
	 * 						=0x33不改变标志位状态，但是临时通知控制板可以出一张票（该功能仅用在禁止控制板自动出票时，临时允许出票一张）
	 */
	public void controlPrintTicket(int control, int cheLei, int cheXing, String carNo) {
		if((control < 0) || (control > 3)) return;
		getSendDataHead((byte)0x42, (byte)0x36);
		getSendData1Byte((byte)(0x30 + control));
		if(control == 3) {
			getSendData1Byte((byte)(0x30 + cheLei));
			getSendData1Byte((byte)(0x30 + cheXing));
			getSendDataCarNo(carNo);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 请取票入场，同时有显示屏显示和语音提示
	 */
	public void takeTicketToIn() {
		getSendDataHead((byte)0x42, (byte)0x37);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 请刷纸票出场，同时有显示屏显示和语音提示
	 */
	public void scanTicketToOut() {
		getSendDataHead((byte)0x42, (byte)0x38);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 纸票未付款，请打开手机微信扫描纸票交费，同时有显示屏显示和语音提示
	 */
	public void ticketPayPlease() {
		getSendDataHead((byte)0x42, (byte)0x39);
		getSendDataEnd();
		send();
	}
	

	/**
	 * 超时未出场，请打开手机微信扫描纸票补交费，同时有显示屏显示和语音提示
	 */
	public void ticketPayMore() {
		getSendDataHead((byte)0x42, (byte)0x3A);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * 下载闲时屏显内容和切换规则，无显示屏显示和语音提示
	 * @param int hangHao 行号取值1-4，0x31表示第一层；=0x32表示第二层；0x33表示第三层；0x34表示第四层
	 * @param int xuHao 节目序号，取值范围0-15，节目切换顺序从0x30->0x3f，注意中间不允许有空的
	 * @param int leiXing 节目类型，取值范围0-8，
	 * 								=0x30表示年月日“17-08-31”；
	 * 								=0x31表示年月日时分星期“2017年08月31日18时24分 星期四”；
	 * 								=0x32表示年“ 2017年 ”；
	 * 								=0x33表示月日“08月31日”；
	 * 								=0x34表示时分“14时35分”；
	 * 								=0x35表示星期“ 星期四 ”；
	 * 								=0x36表示时分秒“14:35:26”；
	 * 								=0x37表示剩余车位“余位:999”或“车位已满”；
	 * 								=0x38表示自定义内容
	 * @param int dispType 显示方式=0立即显示；=1上移显示；=2循环左移 
	 * @param double time 显示时长，取值范围0.0-655.35秒
	 * @param int color 颜色，取值范围0-2，=0x30红色；=0x31绿色；=0x32黄色
	 * @param String dat 自定义节目内容，当节目类型为自定义内容时，长度!=0；当节目类型为其他时，长度=0；
	 */
	public void setDisplayData(int hangHao, int xuHao, int leiXing, int dispType, double time, int color, String dat) {
		if((hangHao < 1) || (hangHao > 4)) return;
		if((xuHao < 0) || (xuHao > 15)) return;
		if((leiXing < 0) || (leiXing > 8)) return;
		if((dispType < 0) || (dispType > 2)) return;
		if((time < 0.0) || (time > 655.35)) return;
		if((color < 0) || (color > 2)) return;
		getSendDataHead((byte)0x42, (byte)0x3B);
		getSendData1Byte((byte)(0x30 + hangHao));
		getSendData1Byte((byte)(0x30 + xuHao));
		getSendData1Byte((byte)(0x30 + leiXing));
		getSendData1Byte((byte)(0x30 + dispType));
		int a = (int)(time * 100);
		byte a1 = (byte)(a >>> 8);
		getSendDataUnboxing(a1);
		a1 = (byte)a;
		getSendDataUnboxing(a1);
		getSendData1Byte((byte)(0x30 + color));
		try {
			byte[] bytes = dat.getBytes("GB2312");
			for(int i = 0; i < bytes.length; i++) {
				getSendData1Byte(bytes[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取闲时屏显内容和切换规则，无显示屏显示和语音提示
	 */
	public void readDisplayData(int hangHao, int xuHao) {
		if((hangHao < 1) || (hangHao > 4)) return;
		if((xuHao < 0) || (xuHao > 15)) return;
		getSendDataHead((byte)0x42, (byte)0x3C);
		getSendData1Byte((byte)(0x30 + hangHao));
		getSendData1Byte((byte)(0x30 + xuHao));
		getSendDataEnd();
		send();
	}
	
	/**
	 * 下载打印机关键词，无显示屏显示和语音提示
	 * @param	String	projectNum 项目编号，由4位字符组成，每个字符为0123456789ABCDEF之一
	 * @param	String	roadNum 车道号，由2位字符组成，每个字符为0123456789ABCDEF之一
	 * @param	String	carType 车辆类型，由1位字符组成，每个字符为0123456789ABCDEF之一
	 * @param	String	url 网址前缀，0<长度<200
	 */
	public void setPrinterParameter(String projectNum, String roadNum, String carType, String url) {
		if(projectNum.length() != 4) return;
		if(roadNum.length() != 2) return;
		if(carType.length() != 1) return;
		if((url.length() == 0) || (url.length() >= 200)) return;
		getSendDataHead((byte)0x42, (byte)0x3E);
		try {
			byte[] tmp1 = projectNum.getBytes("GB2312");
			for(int i = 0; i < tmp1.length; i++) {
				getSendData1Byte(tmp1[i]);
			}
			byte[] tmp2 = roadNum.getBytes("GB2312");
			for(int i = 0; i < tmp2.length; i++) {
				getSendData1Byte(tmp2[i]);
			}
			byte[] tmp5 = roadNum.getBytes("GB2312");
			getSendData1Byte(tmp5[0]);
			byte[] tmp3 = url.getBytes("GB2312");
			for(int i = 0; i < tmp3.length; i++) {
				getSendData1Byte(tmp3[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取打印机关键词，无显示屏显示和语音提示
	 */
	public void readPrinterParameter() {
		getSendDataHead((byte)0x42, (byte)0x3F);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 请等待确认，同时有显示屏显示和语音提示
	 */
	public void pleaseWait() {
		getSendDataHead((byte)0x43, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 账户未注册，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void unRegistered(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x31);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * 账户未授权，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void unAuthorization(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x32);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 账户被禁用，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void beDisabled(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x33);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 账户已过期，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void datePassed(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x34);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * 账户余额不足，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void noEnoughMoney(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x35);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * 车已入场，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void carAlreadyIn(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x36);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * 车未入场，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void carNotIn(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x37);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 软件开闸/落闸，同时有显示屏显示
	 * @param	boolean open_true 为true则开闸，为false则落闸 
	 */
	public void softwareOpenCloseDoor(boolean open_true) {
		if(open_true) {
			getSendDataHead((byte)0x43, (byte)0x40);
		} else {
			getSendDataHead((byte)0x43, (byte)0x41);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 请入场停车，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void pleaseComeIn(String carNo, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3A);
		getSendDataCarNo(carNo);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 请入场停车，剩余使用日期XXXXXX天，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	int 	date_number剩余使用日期，例：=15，即剩余使用日期15天
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void pleaseComeIn_RemainDate(String carNo, int date_number, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3B);
		getSendDataCarNo(carNo);
		try {
			byte[] date;
			date = Integer.toString(date_number).getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 6) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 请入场停车，剩余金额SXXXXX.xx元，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	double 	money_number剩余金额，例：=-300.06，即剩余金额负300.06元
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void pleaseComeIn_RemainMoney(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x3C, carNo, money_number, carType, carClass);
	}
	
	/**
	 * 祝你一路平安，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void goodBye(String carNo, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3D);
		getSendDataCarNo(carNo);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 祝你一路平安，剩余使用日期XXXXXX天，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	int 	date_number剩余使用日期，例：=15，即剩余使用日期15天
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void goodBye_RemainDate(String carNo, int date_number, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3E);
		getSendDataCarNo(carNo);
		try {
			byte[] date;
			date = Integer.toString(date_number).getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 6) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 祝你一路平安，剩余金额SXXXXX.xx元，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	double 	money_number剩余金额，例：=-300.06，即剩余金额负300.06元
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void goodBye_RemainMoney(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x3F, carNo, money_number, carType, carClass);
	}
	
	/**
	 * 请交费SXXXXX.xx元，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	double 	money_number交费金额，例：=128.36，即请交费128.36元
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void pleasePay(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x42, carNo, money_number, carType, carClass);
	}
	
	/**
	 * 对不起，账户已过期，请交费SXXXXX.xx元，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	double 	money_number交费金额，例：=128.36，即请交费128.36元
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void datePassed_PleasePay(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x43, carNo, money_number, carType, carClass);
	}
	
	/**
	 * 对不起，账户余额不足，请交费SXXXXX.xx元，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	double 	money_number交费金额，例：=128.36，即请交费128.36元
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void noEnoughMoney_PleasePay(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x44, carNo, money_number, carType, carClass);
	}
	
	/**
	 * 车位已满，无显示屏显示和语音提示
	 */
	public void lotsFull() {
		getSendDataHead((byte)0x43, (byte)0x45);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 车位有余，无显示屏显示和语音提示
	 * @param	int 	lots_Number剩余车位数量，不能大于9999
	 */
	public void lotsNumber(int lots_Number) {
		getSendDataHead((byte)0x43, (byte)0x46);
		if(lots_Number > 9999) lots_Number = 9999;
		try {
			byte[] date;
			date = Integer.toString(lots_Number).getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 4) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 交费已超时，请到中央管理处补交费，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void payTimeOver(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x4A);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 车未交费，请先到中央收费处交费，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void pleasePayFirst(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x4B);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	

	/**
	 * 请到管理处交费续期，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 */
	public void pleaseGoToManagementAndPay(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x4C);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}


	/**
	 * 停车XX天XX小时XX分，请交费：SXXXXX.xx元，同时有显示屏显示和语音提示
	 * @param	String	carNo 车牌号码
	 * @param	int 	pDay 停车时长的天数
	 * @param	int 	pHour 停车时长的小时数
	 * @param	int 	pMinute 停车时长的分钟数
	 * @param	double 	money_number 停车费用
	 * @param	byte	carType 车型：0x41临时车；0x42月租车；0x43储值车；0x44免费车
	 * @param	byte	carClass 车类：0x41车类A；0x42车类B；0x43车类C；0x44车类D
	 */
	public void parkTimeAndPleasePay(String carNo, int pDay, int pHour, int pMinute, double money_number, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x4D);
		getSendDataCarNo(carNo);
		if(pDay > 99) pDay = 99;
		byte[] date1 = Integer.toString(pDay).getBytes();
		int i = 0;
		while((date1.length + i) < 2) {
			getSendData1Byte((byte)0x30);
			i++;
		}
		for(i = 0; i < date1.length; i++) {
			getSendData1Byte(date1[i]);
		}
		if(pHour > 99) pHour = 99;
		byte[] date2 = Integer.toString(pHour).getBytes();
		i = 0;
		while((date2.length + i) < 2) {
			getSendData1Byte((byte)0x30);
			i++;
		}
		for(i = 0; i < date2.length; i++) {
			getSendData1Byte(date2[i]);
		}
		if(pMinute > 99) pMinute = 99;
		byte[] date3 = Integer.toString(pMinute).getBytes();
		i = 0;
		while((date3.length + i) < 2) {
			getSendData1Byte((byte)0x30);
			i++;
		}
		for(i = 0; i < date3.length; i++) {
			getSendData1Byte(date3[i]);
		}
		getSendDataMoney(money_number);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 调整语音音量，无显示屏显示和语音提示
	 * @param	int	volume 音量，取值范围0-6
	 */
	public void changeVolume(int volume) {
		getSendDataHead((byte)0x43, (byte)0x4E);
		switch(volume) {
		case 0:getSendData1Byte((byte)0xe0);break;
		case 1:getSendData1Byte((byte)0xe1);break;
		case 2:getSendData1Byte((byte)0xe2);break;
		case 3:getSendData1Byte((byte)0xe3);break;
		case 4:getSendData1Byte((byte)0xe4);break;
		case 5:getSendData1Byte((byte)0xe5);break;
		case 6:getSendData1Byte((byte)0xe6);break;
		default:getSendData1Byte((byte)0xe6);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 对不起，车牌号识别错误，请取卡入场(同时吸合3#继电器)，同时有显示屏显示和语音提示
	 */
	public void carNoErr_PleaseTakeCard() {
		getSendDataHead((byte)0x43, (byte)0x4F);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 您的车位已占用，将按临时车入场，同时有显示屏显示和语音提示
	 */
	public void comeInAsGuest() {
		getSendDataHead((byte)0x43, (byte)0x50);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 是否自动切换，无显示屏显示和语音提示
	 * @param	int 	disp_Num屏的行号，取值1-4，例=4则为显示屏第4行
	 * @param	boolean	autoChange_true自动切换标志，=true则自动切换闲时内容，=false则锁定当前内容，不允许切换
	 */
	public void autoChange(int disp_Num, boolean autoChange_true) {
		switch(disp_Num) {
		case 1:getSendDataHead((byte)0x45, (byte)0x30);break;
		case 2:getSendDataHead((byte)0x45, (byte)0x31);break;
		case 3:getSendDataHead((byte)0x45, (byte)0x32);break;
		case 4:getSendDataHead((byte)0x45, (byte)0x33);break;
		default:getSendDataHead((byte)0x45, (byte)0x33);
		}
		if(autoChange_true) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 复位控制板，控制板将复位
	 */
	public void resetBoard() {
		getSendDataHead((byte)0x46, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 显示二维码请扫码入场，有显示屏显示，无语音提示
	 * @param	int		hangHao显示屏行号，取值范围1-4
	 * @param	boolean	xuHao序号，每行分为2帧数据，第一个序号为true，第二个序号为false
	 * @param	byte[]	bitData二维码数据，数组长度必须为128byte
	 */
	public void displayQRCode(int hangHao, boolean xuHao, byte[] bitData) {
		if(bitData.length != 128) return;
		if((hangHao > 4) || (hangHao < 1)) return;
		getSendDataHead((byte)0x46, (byte)0x31);
		switch(hangHao) {
		case 1:getSendData1Byte((byte)0x30);break;
		case 2:getSendData1Byte((byte)0x31);break;
		case 3:getSendData1Byte((byte)0x32);break;
		case 4:getSendData1Byte((byte)0x33);break;
		}
		if(xuHao) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		for(int i = 0; i < bitData.length; i++) {
			getSendDataUnboxing(bitData[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 控制卡机，无显示屏显示和语音提示
	 * @param	boolean	out_true__in_false控制卡机标志，=true则控制卡机出卡，=false则控制卡机吞卡
	 */
	public void controlCardMachine(boolean out_true__in_false) {
		getSendDataHead((byte)0x46, (byte)0x32);
		if(out_true__in_false) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 显示屏显示临时信息，有显示屏显示，无语音提示
	 * @param	int 	disp_Num屏的行号，取值1-4，例=4则为显示屏第4行
	 * @param	int		disp_Type显示方式=0立即显示；=1上移显示；=2循环左移；=3立即显示并且禁止切换；=4上移显示并且禁止切换；=5循环左移并且禁止切换；
	 * @param	String 	disp_Data显示内容
	 */
	public void displayTempMessage(int disp_Num, int disp_Type, String disp_Data) {
		if((disp_Num < 1) || (disp_Num > 4)) return;
		if((disp_Type < 0) || (disp_Type > 5)) return;
		if(disp_Data.length() > 100) return;
		getSendDataHead((byte)0x48, (byte)(0x30 + disp_Num));
		getSendData1Byte((byte)(0x30 + disp_Type));
		try {
			byte[] myByte;
			myByte = disp_Data.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 立即吸合指定的继电器，无显示屏显示和语音提示
	 * @param	byte	relay吸合的继电器名称，bit0-bit3分别代码4个继电器，=1表示吸合，=0表示不控制，例：吸合1#控制器则relay=0x01
	 */
	public void controlRelayAction(byte relay) {
		getSendDataHead((byte)0x49, (byte)0x30);
		getSendDataUnboxing(relay);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 立即吸合指定的继电器，并在指定的时间后释放，无显示屏显示和语音提示
	 * @param	byte	relay吸合的继电器名称，bit0-bit3分别代码4个继电器，=1表示吸合，=0表示不控制，例：吸合1#控制器则relay=0x01
	 * @param	double[] relayTime吸合的时间，数组长度为4，取值为0.0-25.5，单位为秒
	 */
	public void controlRelayAction_TimeOverFree(byte relay, double[] relayTime) {
		if(relayTime.length != 4) return;
		for(int i = 0; i < relayTime.length; i++) {
			if((relayTime[i] < 0.0) || (relayTime[i] > 25.5)) return;
		}
		getSendDataHead((byte)0x49, (byte)0x31);
		for(int i = 0; i < relayTime.length; i++) {
			if((byte)(relay & 0x01) == (byte)0x01) {
				getSendData1Byte((byte)0x31);
			} else {
				getSendData1Byte((byte)0x30);
			}
			getSendDataUnboxing((byte)(relayTime[i] * 10));
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 立即释放指定的继电器，无显示屏显示和语音提示
	 * @param	byte	relay释放的继电器名称，bit0-bit3分别代码4个继电器，=1表示释放，=0表示不控制，例：释放1#控制器则relay=0x01
	 */
	public void controlRelayFree(byte relay) {
		getSendDataHead((byte)0x4A, (byte)0x30);
		getSendDataUnboxing(relay);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取控制板序列号，无显示屏显示和语音提示
	 */
	public void readSerialNumber() {
		getSendDataHead((byte)0x4B, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 处理系统权限，无显示屏显示和语音提示
	 */
	public void copyRight() {
		getSendDataHead((byte)0x4C, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 语音播放指定内容，无显示屏显示，有语音提示
	 * @param	byte[] 	musicNumberArray语音的段号，取值0x00-0xbf，数组长度不超过35
	 */
	public void playMusicArray(byte[] musicNumberArray) {
		if(musicNumberArray.length > 35) return;
		getSendDataHead((byte)0x4D, (byte)0x30);
		for(int i = 0; i < musicNumberArray.length; i++) {
			getSendDataUnboxing(musicNumberArray[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 透明转发RS485，无显示屏显示和语音提示
	 * @param	byte[] 	转发内容，数组长度不超过100
	 */
	public void sendRs485Array(byte[] rs485Array) {
		if(rs485Array.length > 100) return;
		getSendDataHead((byte)0x4E, (byte)0x30);
		for(int i = 0; i < rs485Array.length; i++) {
			getSendDataUnboxing(rs485Array[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取版本号，无显示屏显示和语音提示
	 */
	public void readVersion() {
		getSendDataHead((byte)0x50, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取控制板状态，无显示屏显示和语音提示
	 */
	public void readStatus() {
		getSendDataHead((byte)0x83, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * 读取控制板IO，无显示屏显示和语音提示
	 */
	public void readBoardIO() {
		getSendDataHead((byte)0x83, (byte)0x31);
		getSendDataEnd();
		send();
	}
	
	/**
     * 方法一：
     * byte[] to hex string
     * 
     * @param bytes
     * @return
     */
    public static String bytesToHexFun1(byte[] bytes) {
        // 一个byte为8位，可用两个十六进制位标识
        char[] buf = new char[bytes.length * 3];
        int a = 0;
        int index = 0;
        for(byte b : bytes) { // 使用除与取余进行转换
            if(b < 0) {
                a = 256 + b;
            } else {
                a = b;
            }

            buf[index++] = HEX_CHAR[a / 16];
            buf[index++] = HEX_CHAR[a % 16];
            buf[index++] = ' ';
        }

        return new String(buf);
    }

	/* 发送数据 */
	private void send() {
		if(SendAddIndex == 0) return;
		byte[] sendbuf = new byte[SendAddIndex];
		for(int i = 0; i < sendbuf.length; i++) {
			sendbuf[i] = SendDataBuf[i];
		}
		RecvMessageArea.append("[Send]=" + bytesToHexFun1(sendbuf));
		SendAddIndex = 0;
		if(isTcpFlag) {//通过TCP方式连接设备
			tcpClient.TcpSendData(sendbuf);
		} else {//通过COM方式连接设备
			rs232.ComSendData(sendbuf);
		}
	}
	
	/* 填入发送数据头 */
	private void getSendDataHead(byte fc1, byte fc2) {
		SendCrc = 0;
		SendDataBuf[SendAddIndex++] = SEND_HEAD;
		SendCrc ^= SEND_HEAD;
		SendDataBuf[SendAddIndex++] = fc1;
		SendCrc ^= fc1;
		SendDataBuf[SendAddIndex++] = fc2;
		SendCrc ^= fc2;
	}
	/* 填入发送数据尾 */
	private void getSendDataEnd() {
		byte crc1 = 0;
		byte crc2 = 0;
		SendDataBuf[SendAddIndex++] = SEND_ETX;
		SendCrc ^= SEND_ETX;
		crc1 = (byte)((SendCrc >>> 4) | 0x30);
		crc2 = (byte)((SendCrc & 0x0f) | 0x30);
		SendDataBuf[SendAddIndex++] = crc1;
		SendDataBuf[SendAddIndex++] = crc2;
		SendDataBuf[SendAddIndex++] = SEND_END;
	}
	
	/* 填入发送数据1个字节 */
	private void getSendData1Byte(byte dat) {
		SendDataBuf[SendAddIndex++] = dat;
		SendCrc ^= (byte)dat;
	}
	
	/* 填入发送数据拆分1个字节为2个字节 */
	private void getSendDataUnboxing(byte dat) {
		byte tmp = (byte)(((dat & 0xf0) >>> 4) | 0x30);
		getSendData1Byte(tmp);
		tmp = (byte)((dat & 0x0f) | 0x30);
		getSendData1Byte(tmp);
	}
	
	/* 填入发送数据发送车牌号码  */
	private void getSendDataCarNo(String carNo) {
		try {
			byte[] myByte;
			myByte = carNo.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
				if(i == 10) {break;}
			}
			while(i < 10) {
				getSendData1Byte((byte)0x20);
				i++;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/* 填入发送数据发送金额  */
	private void getSendDataMoney(double money_number) {
		if(money_number >= 0.0) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		money_number = Math.abs(money_number);
		String date_str = Integer.toString((int)(money_number * 100));
		try {
			byte[] date;
			date = date_str.getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 7) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/* 填入发送数据发送金额  */
	private void getSendDataCarNo_Money_CarType_CarClass(byte fc1, byte fc2, String carNo, double money_number, byte carType, byte carClass) {
		getSendDataHead(fc1, fc2);
		getSendDataCarNo(carNo);
		getSendDataMoney(money_number);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/* Index++ */
	private int Index_Inc(int index) {
		index++;
		if(index >= BUF_LENGTH) index = 0;
		return index;
	}
	
	/* 处理接收数据 */
	private void Recv(byte[] recvbuf) {
		for(int i = 0; i < recvbuf.length; i++) {//将刚刚收到的数据移入数据缓冲区
			if(RecvAddIndex == RecvDoneIndex) {
				RecvAddIndex = 0;
				RecvDoneIndex = 0;
			}
			RecvDataBuf[RecvAddIndex] = recvbuf[i];
			RecvAddIndex = Index_Inc(RecvAddIndex);
		}
		int flag = 0;
		int headIndex = 0;
		int etxIndex = 0;
		byte RecvCrc1 = 0;//接收协议的校验码1
		byte RecvCrc2 = 0;//接收协议的校验码2
		RecvCrc = 0;
		while(RecvDoneIndex != RecvAddIndex) {//当缓冲区不为空时，一直处理
			if(flag == 0) {//第0步，找协议头
				if(RecvDataBuf[RecvDoneIndex] == RECV_HEAD) {
					headIndex = RecvDoneIndex;
					RecvCrc = 0;
					flag = 1;
				}
				RecvCrc ^= RecvDataBuf[RecvDoneIndex];//保存校验码
			} else if(flag == 1) {//第1步，找数据结束符，如果又遇见协议头，则标记下新的协议头
				if(RecvDataBuf[RecvDoneIndex] == RECV_HEAD) {
					headIndex = RecvDoneIndex;
					RecvCrc = 0;
				} else if(RecvDataBuf[RecvDoneIndex] == RECV_ETX) {
					etxIndex = RecvDoneIndex;
					flag = 2;
				}
				RecvCrc ^= RecvDataBuf[RecvDoneIndex];//保存校验码
			} else if(flag == 2) {//第2步，收校验码1
				RecvCrc1 = RecvDataBuf[RecvDoneIndex];//接收校验码1
				flag = 3;
			} else if(flag == 3) {//第3步，收校验码2，并判断校验码是否正确
				RecvCrc2 = RecvDataBuf[RecvDoneIndex];//接收校验码2
				int tmp0 = (RecvCrc >>> 4) & 0x0000000f;
				byte tmp1 = (byte)(tmp0 | 0x30);
				byte tmp2 = (byte)((RecvCrc & 0x0f) | 0x30);
				if(tmp1 == RecvCrc1 && tmp2 == RecvCrc2) {
					flag = 4;
				} else {
					flag = 0;
				}
			} else {//第4步，收协议尾，并解析一条命令的协议内容
				if(RecvDataBuf[RecvDoneIndex] == RECV_END) {
					//根据功能码解析协议
					getCommandBack(headIndex, etxIndex);
					printlnRecvData(headIndex, etxIndex);
				}
				flag = 0;
			}
			RecvDoneIndex = Index_Inc(RecvDoneIndex);
		}
		RecvMessageArea.append(RecvDataString);
		RecvDataString = "";
		scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());//控制垂直滚动条到最后面
	}
	
	private void printlnRecvData(int headIndex, int etxIndex) {
		int count = 0;
		int head_tmp = headIndex;
		while(head_tmp != etxIndex) {
			count++;
			head_tmp = Index_Inc(head_tmp);
		}
		count += 4;
		byte[] recv_data = new byte[count];
		for(int i = 0; i < recv_data.length; i++) {
			recv_data[i] = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
		}
		RecvMessageArea.append("[Recv]=" + bytesToHexFun1(recv_data));
	}

	/* 处理接收的完成命令，根据功能码解析收到的协议  */
	private void getCommandBack(int headIndex, int etxIndex) {
		headIndex = Index_Inc(headIndex);
		RecvFc1 = RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvFc2 = RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		switch(RecvFc1) {
		case (byte)0x41:getCommandBack_41(headIndex, etxIndex);break;
		case (byte)0x42:getCommandBack_42(headIndex, etxIndex);break;
		case (byte)0x43:getCommandBack_43(headIndex, etxIndex);break;
		case (byte)0x45:getCommandBack_45(headIndex, etxIndex);break;
		case (byte)0x46:getCommandBack_46(headIndex, etxIndex);break;
		case (byte)0x48:getCommandBack_48(headIndex, etxIndex);break;
		case (byte)0x49:getCommandBack_49(headIndex, etxIndex);break;
		case (byte)0x4A:RecvDataString += "释放继电器命令执行成功\r\n";break;
		case (byte)0x4B:getCommandBack_4B(headIndex, etxIndex);break;
		case (byte)0x4C:RecvDataString += "处理系统权限命令执行成功\r\n";break;
		case (byte)0x4D:RecvDataString += "语音播放指定内容命令执行成功\r\n";break;
		case (byte)0x4E:RecvDataString += "透明转发RS485内容命令执行成功\r\n";break;
		case (byte)0x4F:getCommandBack_4F(headIndex, etxIndex);break;
		case (byte)0x50:getCommandBack_50(headIndex, etxIndex);break;
		case (byte)0x61:getCommandBack_61(headIndex, etxIndex);break;
		case (byte)0x62:RecvDataString += "控制板已复位\r\n";break;
		case (byte)0x71:RecvDataString += "输入信号IN1从无到有（触发地感）\r\n";break;
		case (byte)0x72:RecvDataString += "输入信号IN1从有到无（触发地感）\r\n";break;
		case (byte)0x73:RecvDataString += "输入信号IN2从无到有（防砸地感）\r\n";break;
		case (byte)0x74:RecvDataString += "输入信号IN2从有到无（防砸地感）\r\n";break;
		case (byte)0x75:RecvDataString += "输入信号IN3从无到有（票箱门磁）\r\n";break;
		case (byte)0x76:RecvDataString += "输入信号IN3从有到无（票箱门磁）\r\n";break;
		case (byte)0x77:RecvDataString += "输入信号IN4从无到有（取票按钮）\r\n";break;
		case (byte)0x78:RecvDataString += "输入信号IN4从有到无（取票按钮）\r\n";break;
		case (byte)0x79:RecvDataString += "输入信号WGB_D0从无到有（道闸升到位）\r\n";break;
		case (byte)0x7A:RecvDataString += "输入信号WGB_D0从有到无（道闸升到位）\r\n";break;
		case (byte)0x7B:RecvDataString += "输入信号WGB_D1从无到有（道闸落到位）\r\n";break;
		case (byte)0x7C:RecvDataString += "输入信号WGB_D1从有到无（道闸落到位）\r\n";break;
		case (byte)0x7D:RecvDataString += "输入信号WGA_D0从无到有（道闸门磁）\r\n";break;
		case (byte)0x7E:RecvDataString += "输入信号WGA_D0从有到无（道闸门磁）\r\n";break;
		case (byte)0x7F:RecvDataString += "输入信号WGA_D1从无到有（道闸杆被撞）\r\n";break;
		case (byte)0x80:RecvDataString += "输入信号WGA_D1从有到无（道闸杆被撞）\r\n";break;
		case (byte)0x82:getCommandBack_82(headIndex, etxIndex);break;
		case (byte)0x83:getCommandBack_83(headIndex, etxIndex);break;
		case (byte)0x84:getCommandBack_84(headIndex, etxIndex);break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}
	
	private void getCommandBack_41(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "校对时钟命令执行成功\r\n";break;
		case (byte)0x31:RecvDataString += "设置控制板模式和屏参命令执行成功\r\n";break;
		case (byte)0x32:getCommandBack_4132(headIndex, etxIndex);break;
		case (byte)0x33:RecvDataString += "下载字库文件命令执行成功\r\n";break;
		case (byte)0x34:RecvDataString += "设置其他参数命令执行成功\r\n";break;
		case (byte)0x35:RecvDataString += "下载自动补光时间命令执行成功\r\n";break;
		case (byte)0x36:RecvDataString += "下载音源命令执行成功\r\n";break;
		case (byte)0x37:getCommandBack_4137(headIndex, etxIndex);break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}
	
	/* 读取控制板模式和屏参——返回值解析 */
	private void getCommandBack_4132(int headIndex, int etxIndex) {
		RecvDataString += "读取控制板模式和屏参命令执行成功，";
		switch(RecvDataBuf[headIndex]) {//控制板模式
		case (byte)0x30:RecvDataString += "控制板模式=CP200N模式，";break;
		case (byte)0x31:RecvDataString += "控制板模式=CP200N加一体摄像机，";break;
		case (byte)0x32:RecvDataString += "控制板模式=CP200N加模仿621模式，";break;
		case (byte)0x33:RecvDataString += "控制板模式=CP200N脱机收费模式，";break;
		default:RecvDataString += "控制板模式=未知模式，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//摄像机类型
		case (byte)0x30:RecvDataString += "连接摄像机类型=7098SY，";break;
		case (byte)0x31:RecvDataString += "连接摄像机类型=1类摄像机，";break;
		case (byte)0x32:RecvDataString += "连接摄像机类型=7182SY，";break;
		case (byte)0x33:RecvDataString += "连接摄像机类型=7183SY，";break;
		default:RecvDataString += "连接摄像机类型=" + RecvDataBuf[headIndex] + "类摄像机，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//扫描方式
		case (byte)0x30:RecvDataString += "扫描方式=16扫方式0，";break;
		case (byte)0x31:RecvDataString += "扫描方式=16扫方式1（未实现），";break;
		case (byte)0x32:RecvDataString += "扫描方式=8扫方式0，";break;
		case (byte)0x33:RecvDataString += "扫描方式=8扫方式1（未实现），";break;
		case (byte)0x34:RecvDataString += "扫描方式=4扫方式0，";break;
		case (byte)0x35:RecvDataString += "扫描方式=4扫方式1（未实现），";break;
		default:RecvDataString += "扫描方式=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//屏幕高度
		case (byte)0x30:RecvDataString += "屏幕高度=一行汉字，";break;
		case (byte)0x31:RecvDataString += "屏幕高度=两行汉字，";break;
		case (byte)0x32:RecvDataString += "屏幕高度=三行汉字，";break;
		case (byte)0x33:RecvDataString += "屏幕高度=四行汉字，";break;
		default:RecvDataString += "屏幕高度=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//屏幕宽度
		case (byte)0x30:RecvDataString += "屏幕宽度=4汉字宽度，";break;
		case (byte)0x31:RecvDataString += "屏幕宽度=5汉字宽度（未实现），";break;
		case (byte)0x32:RecvDataString += "屏幕宽度=8汉字宽度，";break;
		default:RecvDataString += "屏幕宽度=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//OE极性
		case (byte)0x30:RecvDataString += "OE极性=正极性，";break;
		case (byte)0x31:RecvDataString += "OE极性=负极性，";break;
		default:RecvDataString += "OE极性=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//数据极性
		case (byte)0x30:RecvDataString += "数据极性=正极性，";break;
		case (byte)0x31:RecvDataString += "数据极性=负极性，";break;
		default:RecvDataString += "数据极性=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//色彩类型
		case (byte)0x30:RecvDataString += "色彩类型=单色，";break;
		case (byte)0x31:RecvDataString += "色彩类型=双基色，";break;
		case (byte)0x32:RecvDataString += "色彩类型=全彩（未实现），";break;
		default:RecvDataString += "色彩类型=未知，";
		}
		RecvDataString += "\r\n";
	}
	
	/* 读取其他参数——返回值解析 */
	private void getCommandBack_4137(int headIndex, int etxIndex) {
		RecvDataString += "读取其他参数命令执行成功，";
		switch(RecvDataBuf[headIndex]) {//语音播报车牌号码
		case (byte)0x30:RecvDataString += "语音播报车牌号码=不播报车牌，";break;
		case (byte)0x31:RecvDataString += "语音播报车牌号码=播报车牌，";break;
		default:RecvDataString += "语音播报车牌号码=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//3号继电器自动控制红绿灯
		case (byte)0x30:RecvDataString += "3号继电器自动控制红绿灯=自动控制，";break;
		case (byte)0x31:RecvDataString += "3号继电器自动控制红绿灯=上位机软件控制，";break;
		default:RecvDataString += "3号继电器自动控制红绿灯=未知，";
		}
		headIndex = Index_Inc(headIndex);
		if(RecvDataBuf[headIndex] != (byte)0x40) {//后面没有其他参数了，就应该退出
			RecvDataString += "\r\n";
			return;
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//满位接口透明转发上位机命令
		case (byte)0x30:RecvDataString += "满位接口透明转发上位机命令=需要转发，";break;
		case (byte)0x31:RecvDataString += "满位接口透明转发上位机命令=不转发，";break;
		default:RecvDataString += "满位接口透明转发上位机命令=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//区分AB接口读卡数据
		case (byte)0x30:RecvDataString += "区分AB接口读卡数据=区分，";break;
		case (byte)0x31:RecvDataString += "区分AB接口读卡数据=不区分，";break;
		default:RecvDataString += "区分AB接口读卡数据=未知，";
		}
		headIndex = Index_Inc(headIndex);
		if(RecvDataBuf[headIndex] != (byte)0x40) {//后面没有其他参数了，就应该退出
			RecvDataString += "\r\n";
			return;
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//竖屏标志
		case (byte)0x30:RecvDataString += "竖屏标志=竖屏，";break;
		case (byte)0x31:RecvDataString += "竖屏标志=正屏，";break;
		default:RecvDataString += "竖屏标志=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//颠倒标志
		case (byte)0x30:RecvDataString += "颠倒标志=整屏颠倒，";break;
		case (byte)0x31:RecvDataString += "竖屏标志=不颠倒，";break;
		default:RecvDataString += "竖屏标志=未知，";
		}
		headIndex = Index_Inc(headIndex);
		if(RecvDataBuf[headIndex] != (byte)0x40) {//后面没有其他参数了，就应该退出
			RecvDataString += "\r\n";
			return;
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//扫描枪串口接卡机标志
		case (byte)0x30:RecvDataString += "扫描枪串口接卡机标志=串口接扫描枪，";break;
		case (byte)0x31:RecvDataString += "扫描枪串口接卡机标志=串口接卡机，";break;
		default:RecvDataString += "扫描枪串口接卡机标志=未知，";
		}
		RecvDataString += "\r\n";
	}
	
	private void getCommandBack_42(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "下载公司名称电话（一层）命令执行成功\r\n";break;
		case (byte)0x31:RecvDataString += "下载公司名称电话（二层）命令执行成功\r\n";break;
		case (byte)0x32:RecvDataString += "下载公司名称电话（三层）命令执行成功\r\n";break;
		case (byte)0x33:RecvDataString += "下载公司名称电话（四层）命令执行成功\r\n";break;
		case (byte)0x35:RecvDataString += "下载纸票打印内容命令执行成功\r\n";break;
		case (byte)0x36:RecvDataString += "下载控制板自动出票标志命令执行成功\r\n";break;
		case (byte)0x37:RecvDataString += "请取票入场命令执行成功\r\n";break;
		case (byte)0x38:RecvDataString += "请刷纸票出场命令执行成功\r\n";break;
		case (byte)0x39:RecvDataString += "纸票未付款，请打开手机微信扫描纸票交费命令执行成功\r\n";break;
		case (byte)0x3A:RecvDataString += "超时未出场，请打开手机微信扫描纸票补交费命令执行成功\r\n";break;
		case (byte)0x3B:RecvDataString += "下载闲时屏显内容和切换规则命令执行成功\r\n";break;
		case (byte)0x3C:getCommandBack_423C(headIndex, etxIndex);break;
		case (byte)0x3D:getCommandBack_423D(headIndex, etxIndex);break;
		case (byte)0x3E:RecvDataString += "下载打印机关键词命令执行成功\r\n";break;
		case (byte)0x3F:getCommandBack_423F(headIndex, etxIndex);break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}
	
	/* 读取闲时屏显内容和切换规则——返回值解析 */
	private void getCommandBack_423C(int headIndex, int etxIndex) {
		RecvDataString += "读取闲时屏显内容和切换规则命令执行成功，";
		RecvDataString += "行号=" + (char)RecvDataBuf[headIndex] + "，";
		headIndex = Index_Inc(headIndex);
		RecvDataString += "节目序号=" + (char)RecvDataBuf[headIndex] + "，";
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//节目类型
		case (byte)0x30:RecvDataString += "节目类型=年月日“17-08-31”，";break;
		case (byte)0x31:RecvDataString += "节目类型=年月日时分星期“2017年08月31日18时24分 星期四”，";break;
		case (byte)0x32:RecvDataString += "节目类型=年“ 2017年 ”，";break;
		case (byte)0x33:RecvDataString += "节目类型=月日“08月31日”，";break;
		case (byte)0x34:RecvDataString += "节目类型=时分“14时35分”，";break;
		case (byte)0x35:RecvDataString += "节目类型=星期“ 星期四 ”，";break;
		case (byte)0x36:RecvDataString += "节目类型=时分秒“14:35:26”，";break;
		case (byte)0x37:RecvDataString += "节目类型=剩余车位“余位:999”或“车位已满”，";break;
		case (byte)0x38:RecvDataString += "节目类型=自定义内容，";break;
		default:RecvDataString += "节目类型=未知，";
		}
		if(RecvDataBuf[headIndex] != (byte)0x0e)
		{
			headIndex = Index_Inc(headIndex);
			switch(RecvDataBuf[headIndex]) {//显示方式
			case (byte)0x30:RecvDataString += "显示方式=换页显示，";break;
			case (byte)0x31:RecvDataString += "显示方式=上移显示，";break;
			case (byte)0x32:RecvDataString += "显示方式=循环左移显示，";break;
			default:RecvDataString += "显示方式=未知，";
			}
			headIndex = Index_Inc(headIndex);
			RecvDataString += "显示时长=" + (char)RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			RecvDataString += (char)RecvDataBuf[headIndex] + ".";
			headIndex = Index_Inc(headIndex);
			RecvDataString += (char)RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			RecvDataString += (char)RecvDataBuf[headIndex] + "秒，";
			headIndex = Index_Inc(headIndex);
			switch(RecvDataBuf[headIndex]) {//颜色
			case (byte)0x30:RecvDataString += "颜色=红色，";break;
			case (byte)0x31:RecvDataString += "颜色=绿色，";break;
			case (byte)0x32:RecvDataString += "颜色=黄色，";break;
			default:RecvDataString += "颜色=未知，";
			}
			headIndex = Index_Inc(headIndex);
			int tmpIndex = headIndex;
			int count = 0;
			while(tmpIndex != etxIndex) {
				count++;
				tmpIndex = Index_Inc(tmpIndex);
			}
			String str = "";
			if(count != 0) {
				byte[] tmpByte = new byte[count];
				for(int i = 0; i < tmpByte.length; i++) {
					tmpByte[i] = RecvDataBuf[headIndex];
					headIndex = Index_Inc(headIndex);
				}
				try {
					str = new String(tmpByte, "GB2312");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			RecvDataString += "自定义节目内容=" + str + "\r\n";
		} else {
			RecvDataString += "无自定义节目内容" + "\r\n";
		}
	}
	
	/* 读取纸票打印内容——返回值解析 */
	private void getCommandBack_423D(int headIndex, int etxIndex) {
		RecvDataString += "读取纸票打印内容命令执行成功，";
		RecvDataString += "行号=" + (char)RecvDataBuf[headIndex] + "，";
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//字体放大
		case (byte)0x30:RecvDataString += "字体放大=正常大小，";break;
		case (byte)0x31:RecvDataString += "字体放大=字体放大1倍，";break;
		case (byte)0x32:RecvDataString += "字体放大=字体放大2倍，";break;
		case (byte)0x33:RecvDataString += "字体放大=字体放大3倍，";break;
		case (byte)0x34:RecvDataString += "字体放大=字体放大4倍，";break;
		default:RecvDataString += "字体放大=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//对齐方式
		case (byte)0x30:RecvDataString += "对齐方式=左对齐，";break;
		case (byte)0x31:RecvDataString += "对齐方式=居中，";break;
		case (byte)0x32:RecvDataString += "对齐方式=右对齐，";break;
		default:RecvDataString += "对齐方式=未知，";
		}
		headIndex = Index_Inc(headIndex);
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "打印内容=" + str + "\r\n";
	}
	
	/* 读取打印机关键词——返回值解析 */
	private void getCommandBack_423F(int headIndex, int etxIndex) {
		RecvDataString += "读取打印机关键词命令执行成功，";
		RecvDataString += "项目编号=" + (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex] + "，";
		headIndex = Index_Inc(headIndex);
		RecvDataString += "车道号=" + (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex] + "，";
		headIndex = Index_Inc(headIndex);
		RecvDataString += "车型=" + (char)RecvDataBuf[headIndex] + "，";
		headIndex = Index_Inc(headIndex);
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "网址前缀=" + str + "\r\n";
	}
	
	private void getCommandBack_43(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "请等待确认命令执行成功\r\n";break;
		case (byte)0x31:RecvDataString += "账户未注册命令执行成功\r\n";break;
		case (byte)0x32:RecvDataString += "账户未授权命令执行成功\r\n";break;
		case (byte)0x33:RecvDataString += "账户被禁用命令执行成功\r\n";break;
		case (byte)0x34:RecvDataString += "账户已过期命令执行成功\r\n";break;
		case (byte)0x35:RecvDataString += "账户余额不足命令执行成功\r\n";break;
		case (byte)0x36:RecvDataString += "车已入场命令执行成功\r\n";break;
		case (byte)0x37:RecvDataString += "车未入场命令执行成功\r\n";break;
		case (byte)0x3a:RecvDataString += "请入场停车命令执行成功\r\n";break;
		case (byte)0x3b:RecvDataString += "请入场停车，账户剩余使用日期XXXXXX天命令执行成功\r\n";break;
		case (byte)0x3c:RecvDataString += "请入场停车，账户剩余金额SXXXXX.xx元命令执行成功\r\n";break;
		case (byte)0x3d:RecvDataString += "祝你一路平安命令执行成功\r\n";break;
		case (byte)0x3e:RecvDataString += "祝你一路平安，账户剩余使用日期XXXXXX天命令执行成功\r\n";break;
		case (byte)0x3f:RecvDataString += "祝你一路平安，账户剩余金额SXXXXX.xx元命令执行成功\r\n";break;
		case (byte)0x40:RecvDataString += "手工开闸命令执行成功\r\n";break;
		case (byte)0x41:RecvDataString += "手工落闸命令执行成功\r\n";break;
		case (byte)0x42:RecvDataString += "请交费：SXXXXX.xx元命令执行成功\r\n";break;
		case (byte)0x43:RecvDataString += "对不起，账户已过期，请交费：SXXXXX.xx元命令执行成功\r\n";break;
		case (byte)0x44:RecvDataString += "对不起，账户余额不足，请交费：SXXXXX.xx元命令执行成功\r\n";break;
		case (byte)0x45:RecvDataString += "车位已满命令执行成功\r\n";break;
		case (byte)0x46:RecvDataString += "车位有余命令执行成功\r\n";break;
		case (byte)0x4a:RecvDataString += "交费已超时命令执行成功\r\n";break;
		case (byte)0x4b:RecvDataString += "车未交费，请先到中央收费处交费命令执行成功\r\n";break;
		case (byte)0x4c:RecvDataString += "请到管理处交费续期命令执行成功\r\n";break;
		case (byte)0x4d:RecvDataString += "停车XX天XX小时XX分，请交费：SXXXXX.xx元命令执行成功\r\n";break;
		case (byte)0x4e:RecvDataString += "调整语音音量命令执行成功\r\n";break;
		case (byte)0x4f:RecvDataString += "对不起，车牌号识别错误，请取卡入场(同时吸合3#继电器)命令执行成功\r\n";break;
		case (byte)0x50:RecvDataString += "您的车位已占用，将按临时车入场命令执行成功\r\n";break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}
	
	private void getCommandBack_45(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "一层是否自动切换命令执行成功\r\n";break;
		case (byte)0x31:RecvDataString += "二层是否自动切换命令执行成功\r\n";break;
		case (byte)0x32:RecvDataString += "三层是否自动切换命令执行成功\r\n";break;
		case (byte)0x33:RecvDataString += "四层是否自动切换命令执行成功\r\n";break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}
	
	private void getCommandBack_46(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "控制板已复位\r\n";break;
		case (byte)0x31:RecvDataString += "显示二维码命令执行成功\r\n";break;
		case (byte)0x32:RecvDataString += "控制卡机命令执行成功\r\n";break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}

	private void getCommandBack_48(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x31:RecvDataString += "一层发送临时显示命令执行成功\r\n";break;
		case (byte)0x32:RecvDataString += "二层发送临时显示命令执行成功\r\n";break;
		case (byte)0x33:RecvDataString += "三层发送临时显示命令执行成功\r\n";break;
		case (byte)0x34:RecvDataString += "四层发送临时显示命令执行成功\r\n";break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}

	private void getCommandBack_49(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "吸合继电器命令执行成功\r\n";break;
		case (byte)0x31:RecvDataString += "吸合指定时间后释放继电器命令执行成功\r\n";break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}

	private void getCommandBack_4B(int headIndex, int etxIndex) {
		if(RecvFc2 == (byte)0x30) {
			RecvDataString += "读取控制板序列号命令执行成功，序列号=";
			for(int i = 0; i < 8; i++) {
				switch(RecvDataBuf[headIndex]) {
				case (byte)0x3a:RecvDataString += 'A';break;
				case (byte)0x3b:RecvDataString += 'B';break;
				case (byte)0x3c:RecvDataString += 'C';break;
				case (byte)0x3d:RecvDataString += 'D';break;
				case (byte)0x3e:RecvDataString += 'E';break;
				case (byte)0x3f:RecvDataString += 'F';break;
				default:RecvDataString += (char)RecvDataBuf[headIndex];
				}
				headIndex = Index_Inc(headIndex);
			}
			RecvDataString += "\r\n";
		} else {
			RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}
	
	private void getCommandBack_4F(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x50:RecvDataString += "设置在线/脱机模式命令执行成功\r\n";break;
		case (byte)0x51:getCommandBack_4F51(headIndex, etxIndex);break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}

	/* 读取在线/脱机模式——返回值解析 */
	private void getCommandBack_4F51(int headIndex, int etxIndex) {
		RecvDataString += "读取在线/脱机模式命令执行成功，";
		switch(RecvDataBuf[headIndex]) {//上电后是否脱机
		case (byte)0x30:RecvDataString += "上电后是否脱机=上电后脱机，";break;
		case (byte)0x31:RecvDataString += "上电后是否脱机=上电后在线，";break;
		default:RecvDataString += "上电后是否脱机=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//当前是否脱机
		case (byte)0x30:RecvDataString += "当前是否脱机=当前脱机，";break;
		case (byte)0x31:RecvDataString += "当前是否脱机=当前在线，";break;
		default:RecvDataString += "当前是否脱机=未知，";
		}
		headIndex = Index_Inc(headIndex);
		RecvDataString += "在线自动转脱机次数=" + RecvDataBuf[headIndex] + "，";
		headIndex = Index_Inc(headIndex);
		byte tmp1 = RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		byte tmp2 = RecvDataBuf[headIndex];
		int tmp3 = ((tmp1 & 0x0f) << 4) | (tmp2 & 0x0f);
		double tmp4 = tmp3 / 100;
		RecvDataString += "上传心跳包时间=" + tmp4 + "秒\r\n";
	}
	
	/* 读取版本号——返回值解析 */
	private void getCommandBack_50(int headIndex, int etxIndex) {
		RecvDataString += "读取版本号命令执行成功，";
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "版本号=" + str + "\r\n";
	}
	
	private void getCommandBack_61(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:getCommandBack_6130(headIndex, etxIndex);break;
		case (byte)0x31:getCommandBack_6131(headIndex, etxIndex);break;
		case (byte)0x32:getCommandBack_6132(headIndex, etxIndex);break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}
	/* 检测到卡机状态变化（每次变化只上传1次）——返回值解析 */
	private void getCommandBack_6130(int headIndex, int etxIndex) {
		RecvDataString += "检测到卡机状态变化（每次变化只上传1次），";
		switch(RecvDataBuf[headIndex]) {//状态1
		case (byte)0x30:RecvDataString += "状态1=正常，";break;
		case (byte)0x31:RecvDataString += "状态1=控制板检测到收卡机塞卡一张，";break;
		case (byte)0x32:RecvDataString += "状态1=控制板检测到出卡机拔卡一张，";break;
		default:RecvDataString += "状态1=未知，";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//状态2
		case (byte)0x30:RecvDataString += "状态2=正常，";break;
		case (byte)0x31:RecvDataString += "状态2=出卡机卡量不足，";break;
		case (byte)0x32:RecvDataString += "状态2=出卡机卡空，";break;
		default:RecvDataString += "状态2=未知，";
		}
		RecvDataString += "\r\n";
	}
	/* WGA口读卡一张（读卡后立即上传，如上位机无应答，则每隔3秒上传一次）——返回值解析 */
	private void getCommandBack_6131(int headIndex, int etxIndex) {
		RecvDataString += "WGA口读卡一张（读卡后立即上传，如上位机无应答，则每隔3秒上传一次），";
		for(int i = 0; i < 8; i++) {
			byte tmp = RecvDataBuf[headIndex];
			switch(tmp) {
			case (byte)0x3a:RecvDataString += 'A';break;
			case (byte)0x3b:RecvDataString += 'B';break;
			case (byte)0x3c:RecvDataString += 'C';break;
			case (byte)0x3d:RecvDataString += 'D';break;
			case (byte)0x3e:RecvDataString += 'E';break;
			case (byte)0x3f:RecvDataString += 'F';break;
			default:RecvDataString += RecvDataBuf[headIndex];
			}
			headIndex = Index_Inc(headIndex);
		}
		RecvDataString += "\r\n";
	}

	/* WGB口读卡一张（读卡后立即上传，如上位机无应答，则每隔3秒上传一次）——返回值解析 */
	private void getCommandBack_6132(int headIndex, int etxIndex) {
		RecvDataString += "WGB口读卡一张（读卡后立即上传，如上位机无应答，则每隔3秒上传一次），";
		for(int i = 0; i < 8; i++) {
			byte tmp = RecvDataBuf[headIndex];
			switch(tmp) {
			case (byte)0x3a:RecvDataString += 'A';break;
			case (byte)0x3b:RecvDataString += 'B';break;
			case (byte)0x3c:RecvDataString += 'C';break;
			case (byte)0x3d:RecvDataString += 'D';break;
			case (byte)0x3e:RecvDataString += 'E';break;
			case (byte)0x3f:RecvDataString += 'F';break;
			default:RecvDataString += RecvDataBuf[headIndex];
			}
			headIndex = Index_Inc(headIndex);
		}
		RecvDataString += "\r\n";
	}
	
	private void getCommandBack_82(int headIndex, int etxIndex) {
		RecvDataString += "上传二维码数据，";
		switch(RecvDataBuf[headIndex]) {//数据类型
		case (byte)0x30:RecvDataString += "数据类型=打印机打印的二维码，";break;
		case (byte)0x31:RecvDataString += "数据类型=扫描枪扫描的二维码，";break;
		default:RecvDataString += "数据类型=未知，";
		}
		headIndex = Index_Inc(headIndex);
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "二维码数据=" + str + "\r\n";
	}

	private void getCommandBack_83(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:getCommandBack_8330(headIndex, etxIndex);break;
		case (byte)0x31:getCommandBack_8331(headIndex, etxIndex);break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}

	private void getCommandBack_84(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:
			RecvDataString += "收到一组调试数据，显示中断时间=";
			byte tmp1,tmp2,tmp3,tmp4;
			int tmpa,tmpb,tmpc,tmpd;
			tmp1 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp2 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp3 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp4 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmpa = ((tmp1 & 0x0f) << 4) | (tmp2 & 0x0f);
			tmpb = ((tmp3 & 0x0f) << 4) | (tmp4 & 0x0f);
			tmpc = (tmpa << 8) | tmpb;
			tmp1 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp2 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp3 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp4 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmpa = ((tmp1 & 0x0f) << 4) | (tmp2 & 0x0f);
			tmpb = ((tmp3 & 0x0f) << 4) | (tmp4 & 0x0f);
			tmpd = (tmpa << 8) | tmpb;
			RecvDataString += tmpc + ", " + tmpd + ", sub=" + (tmpd- tmpc) + "\r\n";
			break;
		default:RecvDataString += "收到一条未知功能的命令\r\n";
		}
	}

	/* 读取/上传控制板状态——返回值解析 */
	private void getCommandBack_8330(int headIndex, int etxIndex) {
		RecvDataString += "读取/上传控制板状态命令执行成功，";
		byte tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x01) {//bit0
			RecvDataString += "打印机无纸错误，";
		} 
		if((byte)(tmp1 & 0x02) == (byte)0x02) {//bit1
			RecvDataString += "打印机纸量少，";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x04) {//bit2
			RecvDataString += "打印机机械故障，";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x08) {//bit3
			RecvDataString += "打印机按钮被按下，";
		} 
		headIndex = Index_Inc(headIndex);
		tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x01) {//bit0
			RecvDataString += "打印机切刀错误，";
		} 
		if((byte)(tmp1 & 0x02) == (byte)0x02) {//bit1
			RecvDataString += "打印机错误，";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x04) {//bit2
			RecvDataString += "打印头温度过高，";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x08) {//bit3
			RecvDataString += "打印机失去连接，";
		} 
		headIndex = Index_Inc(headIndex);
		tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x01) {//bit0
			RecvDataString += "出纸嘴堵塞，";
		} 
		if((byte)(tmp1 & 0x02) == (byte)0x02) {//bit1
			RecvDataString += "超时未取票，";
		} 
		RecvDataString += "\r\n";
	}

	/* 读取控制板输入输出状态——返回值解析 */
	private void getCommandBack_8331(int headIndex, int etxIndex) {
		RecvDataString += "读取控制板输入输出状态命令执行成功，";
		byte tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x00) {//bit0
			RecvDataString += "IN1有输入，";
		}
		if((byte)(tmp1 & 0x02) == (byte)0x00) {//bit1
			RecvDataString += "IN2有输入，";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x00) {//bit2
			RecvDataString += "IN3有输入，";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x00) {//bit3
			RecvDataString += "IN4有输入，";
		} 
		headIndex = Index_Inc(headIndex);
		tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x00) {//bit0
			RecvDataString += "OUT1继电器吸合，";
		}
		if((byte)(tmp1 & 0x02) == (byte)0x00) {//bit1
			RecvDataString += "OUT2继电器吸合，";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x00) {//bit2
			RecvDataString += "OUT3继电器吸合，";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x00) {//bit3
			RecvDataString += "OUT4继电器吸合，";
		} 
		RecvDataString += "\r\n";
	}
}
