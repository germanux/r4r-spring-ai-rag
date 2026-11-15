package com.riansares.r4r.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.*;

/** Only an explicitly trusted socket peer may assert Cloudflare client metadata. Never reads XFF. */
@Component
public final class ClientNetwork {
    public record Details(String address, String country) {}
    private final Set<String> peers;
    private final boolean enabled;
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
    public ClientNetwork(@Value("${r4r.api-security.trusted-proxy-addresses:}") String peers,
                         @Value("${r4r.api-security.trust-proxy-headers:false}") boolean enabled) {
        this.peers = new HashSet<>();
        for (String peer : peers.split(",")) if (!peer.isBlank()) this.peers.add(peer.trim());
        this.enabled = enabled;
    }
    public Details resolve(HttpServletRequest request) {
        if (request == null) return new Details(null,null);
        String address = literal(request.getRemoteAddr()), country = null;
        if (enabled && peers.contains(request.getRemoteAddr())) {
            String asserted = literal(request.getHeader("CF-Connecting-IP"));
            if (asserted != null) {
                address = asserted;
                String code = request.getHeader("CF-IPCountry");
                if (code != null && COUNTRIES.contains(code)) country = code;
            }
        }
        return new Details(address,country);
    }
    public Details publicDetails(HttpServletRequest request) {
        Details details = resolve(request);
        if (details.address() == null) return new Details(null,null);
        try {
            var address = InetAddress.getByName(details.address());
            byte[] bytes=address.getAddress();
            boolean privateV6 = bytes.length==16 && ((bytes[0]&0xe0)!=0x20
                    || (bytes[0]==0x20 && bytes[1]==0x01 && (bytes[2]&0xff)==0x0d && (bytes[3]&0xff)==0xb8));
            boolean reservedV4 = false;
            if (bytes.length==4) {
                int first=bytes[0]&255, second=bytes[1]&255, third=bytes[2]&255;
                reservedV4 = first==0 || first>=224 || (first==100 && second>=64 && second<=127)
                    || (first==198 && (second==18 || second==19 || second==51 && third==100))
                    || (first==192 && second==0 && (third==0 || third==2))
                    || (first==203 && second==0 && third==113);
            }
            if (reservedV4 || privateV6 || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress()) return new Details(null,null);
            return details;
        } catch (java.net.UnknownHostException ignored) { return new Details(null,null); }
    }
    static String literal(String value) {
        if (value == null || value.length()>45 || !value.matches("[0-9a-fA-F:.]+")) return null;
        if (!value.contains(":") && !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (java.net.UnknownHostException ignored) { return null; }
    }
}
