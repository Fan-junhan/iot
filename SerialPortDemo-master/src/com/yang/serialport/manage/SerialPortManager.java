package com.yang.serialport.manage;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import com.yang.serialport.exception.NoSuchPort;
import com.yang.serialport.exception.NotASerialPort;
import com.yang.serialport.exception.PortInUse;
import com.yang.serialport.exception.ReadDataFromSerialPortFailure;
import com.yang.serialport.exception.SendDataToSerialPortFailure;
import com.yang.serialport.exception.SerialPortInputStreamCloseFailure;
import com.yang.serialport.exception.SerialPortOutputStreamCloseFailure;
import com.yang.serialport.exception.SerialPortParameterFailure;
import com.yang.serialport.exception.TooManyListeners;
import com.yang.serialport.utils.ArrayUtils;

/**
 * 实验4.2 串口管理类
 * 功能：打开串口、关闭串口、监听串口事件、读取并显示数据、发送下行命令
 */
public class SerialPortManager {

    private SerialPort serialPort;       // 串口对象
    private InputStream inputStream;     // 输入流
    private OutputStream outputStream;   // 输出流

    /**
     * 打开串口
     *
     * @param portName 串口号（如 "COM5"）
     * @param baudRate 波特率（如 115200）
     * @throws Exception 打开失败时抛出异常
     */
    public void openPort(String portName, int baudRate) throws Exception {
        CommPortIdentifier portId = null;
        Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();

        // 查找指定串口
        while (portList.hasMoreElements()) {
            CommPortIdentifier currPortId = (CommPortIdentifier) portList.nextElement();
            if (currPortId.getPortType() == CommPortIdentifier.PORT_SERIAL &&
                currPortId.getName().equals(portName)) {
                portId = currPortId;
                break;
            }
        }

        if (portId == null) {
            throw new Exception("❌ 未找到端口：" + portName);
        }

        // 打开串口
        serialPort = (SerialPort) portId.open(portName, 2000);
        serialPort.setSerialPortParams(
                baudRate,
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE
        );

        // 获取输入输出流
        inputStream = serialPort.getInputStream();
        outputStream = serialPort.getOutputStream();

        // 添加监听器
        addListener();

        ShowUtils.showData("✅ 串口已打开: " + portName + " @ " + baudRate + "bps");
    }

    /**
     * 添加串口事件监听器
     */
    private void addListener() {
        try {
            serialPort.addEventListener(new SerialPortEventListener() {
                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
                        readData();
                    }
                }
            });
            serialPort.notifyOnDataAvailable(true);
        } catch (TooManyListenersException e) {
            ShowUtils.showData("⚠️ 监听器添加失败: " + e.getMessage());
        }
    }

    /**
     * 从串口读取数据
     */
    private void readData() {
        try {
            byte[] buffer = new byte[1024];
            int len = inputStream.read(buffer);
            if (len > 0) {
                byte[] realData = new byte[len];
                System.arraycopy(buffer, 0, realData, 0, len);
                String hexData = ByteUtils.byteArrayToHexString(realData);
                ShowUtils.showData("📥 接收到数据: " + hexData);
            }
        } catch (IOException e) {
            ShowUtils.showData("❌ 读取数据失败: " + e.getMessage());
        }
    }

    /**
     * 向串口发送数据（用于下行命令）
     *
     * @param data 待发送的字节数组
     */
    public void sendToPort(byte[] data) {
        try {
            if (outputStream != null) {
                outputStream.write(data);
                outputStream.flush();
                ShowUtils.showData("📤 已发送: " + ByteUtils.byteArrayToHexString(data));
            } else {
                ShowUtils.showData("❌ 串口未打开，无法发送。");
            }
        } catch (IOException e) {
            ShowUtils.showData("❌ 发送失败: " + e.getMessage());
        }
    }

    /**
     * 关闭串口
     */
    public void closePort() {
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (serialPort != null) {
                serialPort.removeEventListener();
                serialPort.close();
                serialPort = null;
            }
            ShowUtils.showData("⚙️ 串口已关闭。");
        } catch (IOException e) {
            ShowUtils.showData("❌ 关闭串口失败: " + e.getMessage());
        }
    }

    /**
     * 判断串口是否已打开
     *
     * @return true = 已打开
     */
    public boolean isOpened() {
        return serialPort != null;
    }
}
