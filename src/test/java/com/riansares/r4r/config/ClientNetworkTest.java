package com.riansares.r4r.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class ClientNetworkTest {
    private MockHttpServletRequest request(String peer,String ip,String country) {
        var r=new MockHttpServletRequest();r.setRemoteAddr(peer);
        if(ip!=null)r.addHeader("CF-Connecting-IP",ip);if(country!=null)r.addHeader("CF-IPCountry",country);
        r.addHeader("X-Forwarded-For","1.1.1.1");return r;
    }
    @Test void trustsCloudflareOnlyFromConfiguredSocketPeer() {
        var n=new ClientNetwork("10.0.0.2",true);
        var d=n.publicDetails(request("10.0.0.2","8.8.8.8","US"));
        assertEquals("8.8.8.8",d.address());assertEquals("US",d.country());
        d=n.publicDetails(request("9.9.9.9","8.8.8.8","US"));
        assertEquals("9.9.9.9",d.address());assertNull(d.country());
    }
    @Test void supportsIpv6AndNeverInventsCountry() {
        var n=new ClientNetwork("10.0.0.2",true);
        var d=n.publicDetails(request("10.0.0.2","2001:4860:4860::8888","ES"));
        assertNotNull(d.address());assertTrue(d.address().contains(":"));assertEquals("ES",d.country());
        assertNull(n.publicDetails(request("10.0.0.2","8.8.8.8","XX")).country());
        assertNull(n.publicDetails(request("10.0.0.2","8.8.8.8",null)).country());
    }
    @Test void noHostnameLookupOrPrivateAddressPersistence() {
        var n=new ClientNetwork("10.0.0.2",true);
        assertNull(n.publicDetails(request("10.0.0.2","attacker.example","GB")).address());
        assertNull(n.publicDetails(request("10.0.0.2","192.168.1.1","ES")).address());
        assertNull(n.publicDetails(request("10.0.0.2","fc00::1","ES")).address());
        assertNull(n.publicDetails(request("10.0.0.2","100.64.0.1","ES")).address());
        assertNull(n.publicDetails(request("10.0.0.2","203.0.113.10","ES")).address());
        assertNull(n.publicDetails(request("10.0.0.2","2001:db8::1","ES")).address());
        assertNull(new ClientNetwork("10.0.0.2",false).publicDetails(request("10.0.0.2","8.8.8.8","US")).address());
    }
    @Test void missingMalformedAndRepeatedHeadersNeverInventAnAddressOrCountry() {
        var n = new ClientNetwork("172.20.0.6", true);
        for (String value : new String[]{null, "8.8.8.8,1.1.1.1", "bad", "8.8.8.8:443"}) {
            var result = n.publicDetails(request("172.20.0.6", value, "ES"));
            assertNull(result.address()); assertNull(result.country());
        }
        var duplicate = request("172.20.0.6", "8.8.8.8", "ES");
        duplicate.addHeader("CF-Connecting-IP", "1.1.1.1");
        assertNull(n.publicDetails(duplicate).address());
        var duplicateCountry = request("172.20.0.6", "8.8.8.8", "ES");
        duplicateCountry.addHeader("CF-IPCountry", "IE");
        assertNull(n.publicDetails(duplicateCountry).country());
        assertNull(n.publicDetails(request("172.20.0.6", "8.8.8.8", "T1")).country());
    }
    @Test void equivalentIpv6ProxyLiteralsMatchWithoutAHostnameLookup() {
        var n = new ClientNetwork("fd00::2", true);
        var result = n.publicDetails(request("fd00:0:0:0:0:0:0:2", "2001:4860:4860::8888", "IE"));
        assertEquals("2001:4860:4860:0:0:0:0:8888", result.address());
        assertEquals("IE", result.country());
    }
    @Test void preservesOriginalIpv6WhenCloudflareOverwritesItsIpv4Header() {
        var n = new ClientNetwork("172.20.0.6", true);
        var r = request("172.20.0.6", "240.1.2.3", "IE");
        r.addHeader("CF-Connecting-IPv6", "2606:4700:4700::1111");
        assertEquals("2606:4700:4700:0:0:0:0:1111", n.publicDetails(r).address());
        assertEquals("IE", n.publicDetails(r).country());
        r.setRemoteAddr("172.20.0.1");
        assertNull(n.publicDetails(r).address());
        assertNull(n.publicDetails(r).country());
    }
    @Test void rejectsWildcardSubnetAndHostnameProxyConfiguration() {
        for (String peer : new String[]{"0.0.0.0/0", "172.20.0.0/16", "*", "cloudflared", "0.0.0.0", "::"})
            assertThrows(IllegalArgumentException.class, () -> new ClientNetwork(peer, true));
    }
}
