package org.example.server.modules;

import java.net.*;
import java.util.*;

/**
 * Manages network address discovery and display
 * 
 * LESSON 5: IP Addressing and Network Interfaces
 * Shows how to discover local network addresses programmatically
 */
public class NetworkUtil {
    
    /**
     * Display all available network addresses
     * 
     * LESSON 5: Using NetworkInterface and InetAddress classes
     */
    public static void displayNetworkAddresses(int port) {
        try {
            System.out.println("\n🌐 Server Network Addresses:");
            System.out.println("   Local: ws://localhost:" + port);
            
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // Skip loopback and inactive interfaces
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // Only show IPv4 addresses
                    if (addr instanceof Inet4Address) {
                        System.out.println("   Network: ws://" + addr.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Error getting network addresses: " + e.getMessage());
        }
    }
    
    /**
     * Get primary local IP address
     */
    public static String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Error getting local IP: " + e.getMessage());
        }
        return "localhost";
    }
}
