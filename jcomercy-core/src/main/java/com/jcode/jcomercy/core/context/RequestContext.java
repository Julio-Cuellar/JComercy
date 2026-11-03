package com.jcode.jcomercy.core.context;

import com.jcode.jcomercy.core.identifiers.CausationId;
import com.jcode.jcomercy.core.identifiers.CorrelationId;
import com.jcode.jcomercy.core.identifiers.PublicAccountId;
import com.jcode.jcomercy.core.identifiers.PublicBranchId;
import com.jcode.jcomercy.core.identifiers.PublicCompanyId;
import com.jcode.jcomercy.core.identifiers.PublicUserId;

import java.util.Objects;
import java.util.Optional;

public record RequestContext(
        PublicUserId publicUserId,
        Optional<PublicAccountId> publicAccountId,
        Optional<PublicCompanyId> publicCompanyId,
        Optional<PublicBranchId> publicBranchId,
        CorrelationId correlationId,
        Optional<CausationId> causationId) {

    public RequestContext {
        Objects.requireNonNull(publicUserId, "publicUserId must not be null");
        publicAccountId = Objects.requireNonNull(publicAccountId, "publicAccountId must not be null");
        publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId must not be null");
        publicBranchId = Objects.requireNonNull(publicBranchId, "publicBranchId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        causationId = Objects.requireNonNull(causationId, "causationId must not be null");
    }

    public static RequestContext forUser(PublicUserId publicUserId) {
        return new RequestContext(
                publicUserId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CorrelationId.newId(),
                Optional.empty());
    }
}
