package org.box1.warmup;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Minimal endpoint so the warm-up build exercises Quarkus build, test and integration-test bootstrap. */
@Path("/warmup")
public class WarmupResource {

    /**
     * Returns a fixed marker.
     *
     * @return the marker text
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String warmup() {
        return "box1-builder";
    }
}
