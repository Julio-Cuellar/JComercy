package com.jcode.jcomercy.companymanagement.domain.model.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class BranchAssignment {
    private final UUID id;
    private final String publicAssignmentId;
    private final String publicMembershipId;
    private final String publicBranchId;
    private AssignmentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public BranchAssignment(UUID id, String publicAssignmentId, String publicMembershipId, String publicBranchId, AssignmentStatus status, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicAssignmentId = Objects.requireNonNull(publicAssignmentId, "publicAssignmentId no puede ser nulo");
        this.publicMembershipId = Objects.requireNonNull(publicMembershipId, "publicMembershipId no puede ser nulo");
        this.publicBranchId = Objects.requireNonNull(publicBranchId, "publicBranchId no puede ser nulo");
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static BranchAssignment create(String publicMembershipId, String publicBranchId) {
        Instant now = Instant.now();
        String publicId = "asg_" + UUID.randomUUID().toString().replace("-", "");
        return new BranchAssignment(UUID.randomUUID(), publicId, publicMembershipId, publicBranchId, AssignmentStatus.ACTIVE, now, now, 0L);
    }

    public void suspend() {
        this.status = AssignmentStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = AssignmentStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getPublicAssignmentId() { return publicAssignmentId; }
    public String getPublicMembershipId() { return publicMembershipId; }
    public String getPublicBranchId() { return publicBranchId; }
    public AssignmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
