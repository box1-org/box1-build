package org.box1.warmup;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WarmupResourceTest {

    @Test
    void returnsMarker() {
        given().when().get("/warmup").then().statusCode(200).body(is("box1-builder"));
    }
}
