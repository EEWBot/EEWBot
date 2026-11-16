package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.entity.dmdata.api.DmdataError;
import org.jetbrains.annotations.Nullable;

public class DmdataGatewayException extends EEWGatewayException {

    private final DmdataError dmdataError;

    public DmdataGatewayException(DmdataError dmdataError) {
        super(dmdataError.toString());
        this.dmdataError = dmdataError;
    }

    /** For responses that carry no parseable DMDATA error body (e.g. a proxy's HTML 502). */
    public DmdataGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.dmdataError = null;
    }

    public @Nullable DmdataError getDmdataError() {
        return this.dmdataError;
    }
}
