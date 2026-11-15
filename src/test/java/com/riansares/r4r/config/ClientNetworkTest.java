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
}
