package cn.linkdeck.adb;

import java.net.ServerSocket;

public final class TcpCandidateProbeChecks {
  public static void main(String[] args)throws Exception{
    try(ServerSocket server=new ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){
      if(!TcpCandidateProbe.open("127.0.0.1",server.getLocalPort(),400))throw new AssertionError("reachable saved address not found");
      if(TcpCandidateProbe.open("127.0.0.1",0,400))throw new AssertionError("invalid port accepted");
      if(TcpCandidateProbe.open("",server.getLocalPort(),400))throw new AssertionError("empty host accepted");
      System.out.println("TCP CANDIDATE PROBE PASSED: 3 checks");
    }
  }
}
