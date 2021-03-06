package org.ietf.jose.jwt;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.ietf.jose.util.JsonMarshaller;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Andrius Druzinis-Vitkus
 * @since 0.0.1 created 03/12/2017
 */
public class JwtClaimsTest {

  @Test
  public void equals() throws IOException, Exception {
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.SECONDS);

    JwtClaims claim = new JwtClaims();
    claim.setAudience("someAudience");
    claim.setIssuedAt(now);
    claim.setNotBefore(now.plusHours(1));
    claim.setExpirationTime(now.plusHours(2));
//    claim.addClaim("privateName", "privateValue");

    String json = JsonMarshaller.toJson(claim);
    String jsonDirect = claim.toJson();

    System.out.println("  JsonMarshaller.toJson: " + json);
    System.out.println("  claim.toJson         : " + jsonDirect);
    // test json text
    assertEquals(json, jsonDirect);
    // test object vs reconstituted object
//    JwtClaims reconstituted = JsonMarshaller.fromJson(json, JwtClaims.class);
//    System.out.println("original      " + claim.toJson());
//    System.out.println("  with array " + reconstituted.getClaims() == null);
//    System.out.println("reconstituted " + reconstituted.toJson());
//    System.out.println("  with array " + reconstituted.getClaims() == null);

    /**
     * BUG: The JsonMarshaller FAILS to read or write JWT private claims.
     * <p>
     * TODO: Add an XML adapter for the claims field.
     */
    assertEquals(claim, JsonMarshaller.fromJson(json, JwtClaims.class));
//    java.lang.AssertionError:
//    expected: org.ietf.jose.jwt.JwtClaims<{"aud":"someAudience","exp":1527964352,"nbf":1527960752,"iat":1527957152}>
//    but was : org.ietf.jose.jwt.JwtClaims<{"aud":"someAudience","exp":1527964352,"nbf":1527960752,"iat":1527957152}>
    // test object vs. directly reconstituted object
    JwtClaims reconstituted = JwtClaims.fromJson(json);
    System.out.println("  original      " + claim.toJson());
    System.out.println("  reconstituted " + reconstituted.toJson());
    assertEquals(claim, JwtClaims.fromJson(json));
    System.out.println("JwtClaimsTest equals   OK ");
  }

  @Test
  public void testCustomClaims() throws IOException, Exception {
    JwtClaims claims = new JwtClaims();
    claims.addClaim("email", "foo@bar.com");
    claims.addClaim("friends", Arrays.asList("John", "Jack", "Jeremy"));

    String json = claims.toJson();
    System.out.println("  toJson   " + json);
    JwtClaims deserialized = JwtClaims.fromJson(json);
    assertEquals(claims, deserialized);

    System.out.println("JwtClaimsTest testCustomClaims   OK ");
  }
}
