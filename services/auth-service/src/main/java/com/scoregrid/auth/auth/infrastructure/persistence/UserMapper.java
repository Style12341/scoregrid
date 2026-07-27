package com.scoregrid.auth.auth.infrastructure.persistence;

import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity to domain. The direction that matters — a schema change stops here
 * instead of reaching the business rules.
 *
 * <p>Domain to entity stays hand-written in {@code UserRepositoryAdapter}: it
 * has to resolve {@link Role} values against seeded {@code roles} rows, which
 * is a lookup, not a mapping.
 */
/*
 * disableBuilder: User.withId() returns a User, which MapStruct otherwise
 * reads as a builder method and then reports "withId" as an unmapped target
 * property. It is a copy-with, not a builder — map through the constructor.
 */
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        builder = @Builder(disableBuilder = true))
interface UserMapper {

    // "withId" is not a property. User.withId() is a copy-with returning a new
    // User, and MapStruct picks it up as a wither-style target accessor; with
    // unmappedTargetPolicy = ERROR that fails the build. Ignoring it keeps the
    // strict policy — which is the point, it caught this — for real properties.
    @Mapping(target = "withId", ignore = true)
    @Mapping(target = "passwordHash", source = "password")
    User toDomain(UserEntity entity);

    default Role toRole(RoleEntity role) {
        return Role.of(role.getName());
    }
}
