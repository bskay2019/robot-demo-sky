package com.robot.demo.southbound.protocol;

import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 二进制帧 <-> Packet
 * 小白提示：DataOutputStream 默认用「大端」写多字节整数，和多数网络协议一致。
 */
public final class PacketCodec {
    private PacketCodec() {
    }

    /** 编码：云端发、车端发都用这个 */
    public static byte[] encode(int msgType,String bodyJson){
        byte[] body=!StringUtils.hasText(bodyJson)?new byte[0]:bodyJson.getBytes(StandardCharsets.UTF_8);

        try {
            ByteArrayOutputStream bos=new ByteArrayOutputStream(ProtocolConstants.HEADER_LENGTH+ body.length);
            DataOutputStream out=new DataOutputStream(bos);
            out.writeInt(ProtocolConstants.MAGIC); //4
            out.writeByte(ProtocolConstants.VERSION); //1
            out.writeShort(msgType & 0xFFFF); //2 （只要低16位）
            out.writeInt(body.length); //4
            out.write(body); //N
            out.flush();

            return bos.toByteArray();

        } catch (IOException e) {
            // 内存流几乎不会失败；转成运行时异常方便上层处理
            throw new IllegalStateException("encode failed", e);
        }

    }


    /** 解码（TCP）：从流里「刚好」读一帧 —— 解决粘包/半包 */
    public static Packet decode(InputStream in) throws IOException{
        DataInputStream dataInputStream=(in instanceof DataInputStream)?(DataInputStream) in:new DataInputStream(in);
        int magic=dataInputStream.readInt();
        if (magic!=ProtocolConstants.MAGIC){
            throw new IOException("bad magic: 0x"+Integer.toHexString(magic));
        }

        int version=dataInputStream.readUnsignedByte();
        if(version!= ProtocolConstants.VERSION){
            throw new IOException("bad version: "+version);
        }

        int msgType=dataInputStream.readUnsignedShort();
        int bodyLen=dataInputStream.readInt();
        if(bodyLen<0 || bodyLen>1_000_000){
            throw new IOException("bad bodyLen: "+bodyLen);
        }

        byte[] bodyBytes = dataInputStream.readNBytes(bodyLen);
        if(bodyBytes.length!=bodyLen){
            throw new IOException("stream closed while reading body");
        }

        String bodyJson= bodyLen==0?"{}":new String(bodyBytes,StandardCharsets.UTF_8);
        return new Packet(msgType,bodyJson);
    }

    /** 解码（UDP）：UDP 一次 receive 就是一整包 */
    public static Packet decode(byte[]data,int offset,int length) throws IOException{
        if(length<ProtocolConstants.HEADER_LENGTH){
            throw new IOException("udp packet too short");
        }
        return decode(new ByteArrayInputStream(data,offset,length));
    }
}
