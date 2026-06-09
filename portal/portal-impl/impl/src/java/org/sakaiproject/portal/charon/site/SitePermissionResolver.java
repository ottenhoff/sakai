/**********************************************************************************
 * Copyright (c) 2005, 2006, 2007, 2008 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 **********************************************************************************/
package org.sakaiproject.portal.charon.site;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolManager;

/**
 * Batched site permission resolution for portal navigation rendering.
 */
public final class SitePermissionResolver {

	public static final SitePermissionResolver DIRECT = new SitePermissionResolver(SitePermissionStrategy.direct());
	public static final SitePermissionResolver EMPTY_BULK = new SitePermissionResolver(
			SitePermissionStrategy.bulkAll(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()));

	private final SitePermissionStrategy strategy;

	private SitePermissionResolver(SitePermissionStrategy strategy) {
		this.strategy = strategy;
	}

	public boolean canUpdateSite(Site site, SecurityService securityService) {
		return strategy.canUpdate(site, securityService);
	}

	public boolean isInstructor(Site site, SecurityService securityService) {
		return strategy.isInstructor(site, securityService);
	}

	public boolean isFirstToolVisibleToAnyNonMaintainerRole(Site site, SitePage page, ToolManager toolManager) {
		return strategy.isFirstToolVisibleToAnyNonMaintainerRole(site, page, toolManager);
	}

	public static SitePermissionResolver forPageLocks(Collection<Site> sites, AuthzGroupService authzGroupService,
			SecurityService securityService, SessionManager sessionManager) {
		return build(sites, true, authzGroupService, securityService, sessionManager);
	}

	public static SitePermissionResolver forSecurityChecks(Collection<Site> sites, AuthzGroupService authzGroupService,
			SecurityService securityService, SessionManager sessionManager) {
		return build(sites, false, authzGroupService, securityService, sessionManager);
	}

	private static SitePermissionResolver build(Collection<Site> sites, boolean includeRoleFunctions,
			AuthzGroupService authzGroupService, SecurityService securityService, SessionManager sessionManager) {

		if (sites == null || sites.isEmpty()) {
			return EMPTY_BULK;
		}

		List<String> siteRefs = sites.stream()
				.filter(Objects::nonNull)
				.map(Site::getReference)
				.distinct()
				.collect(Collectors.toList());

		if (siteRefs.isEmpty()) {
			return EMPTY_BULK;
		}

		Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef = includeRoleFunctions
				? filterNonMaintainerRoleFunctions(authzGroupService.getRoleFunctions(siteRefs))
				: Collections.emptyMap();

		if (securityService.isUserRoleSwapped()) {
			return new SitePermissionResolver(includeRoleFunctions
					? SitePermissionStrategy.bulkRoleFunctionsOnly(nonMaintainerRoleFunctionsBySiteRef)
					: SitePermissionStrategy.direct());
		}

		String userId = sessionManager.getCurrentSessionUserId();
		if (StringUtils.isBlank(userId)) {
			return new SitePermissionResolver(includeRoleFunctions
					? SitePermissionStrategy.bulkAll(Collections.emptySet(), Collections.emptySet(), nonMaintainerRoleFunctionsBySiteRef)
					: SitePermissionStrategy.bulkSecurityOnly(Collections.emptySet(), Collections.emptySet()));
		}

		if (securityService.isSuperUser()) {
			Set<String> refs = new HashSet<>(siteRefs);
			return new SitePermissionResolver(includeRoleFunctions
					? SitePermissionStrategy.bulkAll(refs, refs, nonMaintainerRoleFunctionsBySiteRef)
					: SitePermissionStrategy.bulkSecurityOnly(refs, refs));
		}

		Set<String> siteUpdaterRefs = authzGroupService.getAuthzGroupsIsAllowed(userId, SiteService.SECURE_UPDATE_SITE, siteRefs);
		Set<String> instructorRefs = authzGroupService.getAuthzGroupsIsAllowed(userId, "section.role.instructor", siteRefs);
		return new SitePermissionResolver(includeRoleFunctions
				? SitePermissionStrategy.bulkAll(siteUpdaterRefs, instructorRefs, nonMaintainerRoleFunctionsBySiteRef)
				: SitePermissionStrategy.bulkSecurityOnly(siteUpdaterRefs, instructorRefs));
	}

	private static Map<String, Collection<Set<String>>> filterNonMaintainerRoleFunctions(
			Map<String, Map<String, Set<String>>> roleFunctionsBySiteRef) {
		if (roleFunctionsBySiteRef == null || roleFunctionsBySiteRef.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef = new HashMap<>();
		roleFunctionsBySiteRef.forEach((siteRef, roleFunctions) -> {
			List<Set<String>> nonMaintainerRoleFunctions = Optional.ofNullable(roleFunctions)
					.orElse(Collections.emptyMap())
					.values().stream()
					.filter(functions -> !functions.contains(SiteService.SECURE_UPDATE_SITE))
					.collect(Collectors.toList());
			nonMaintainerRoleFunctionsBySiteRef.put(siteRef, nonMaintainerRoleFunctions);
		});

		return nonMaintainerRoleFunctionsBySiteRef;
	}

	private interface SitePermissionStrategy {

		boolean canUpdate(Site site, SecurityService securityService);

		boolean isInstructor(Site site, SecurityService securityService);

		boolean isFirstToolVisibleToAnyNonMaintainerRole(Site site, SitePage page, ToolManager toolManager);

		static SitePermissionStrategy direct() {
			return new DirectSecurityStrategy();
		}

		static SitePermissionStrategy bulkSecurityOnly(Set<String> siteUpdaterRefs, Set<String> instructorRefs) {
			return new BulkSecurityStrategy(siteUpdaterRefs, instructorRefs);
		}

		static SitePermissionStrategy bulkRoleFunctionsOnly(
				Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef) {
			return new BulkRoleFunctionsStrategy(nonMaintainerRoleFunctionsBySiteRef);
		}

		static SitePermissionStrategy bulkAll(Set<String> siteUpdaterRefs, Set<String> instructorRefs,
				Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef) {
			return new BulkAllStrategy(siteUpdaterRefs, instructorRefs, nonMaintainerRoleFunctionsBySiteRef);
		}
	}

	private static boolean canUpdateViaSecurity(Site site, SecurityService securityService) {
		return securityService.unlock(SiteService.SECURE_UPDATE_SITE, site.getReference());
	}

	private static boolean isInstructorViaSecurity(Site site, SecurityService securityService) {
		return securityService.unlock("section.role.instructor", site.getReference());
	}

	private static final class DirectSecurityStrategy implements SitePermissionStrategy {

		@Override
		public boolean canUpdate(Site site, SecurityService securityService) {
			return canUpdateViaSecurity(site, securityService);
		}

		@Override
		public boolean isInstructor(Site site, SecurityService securityService) {
			return isInstructorViaSecurity(site, securityService);
		}

		@Override
		public boolean isFirstToolVisibleToAnyNonMaintainerRole(Site site, SitePage page, ToolManager toolManager) {
			return toolManager.isFirstToolVisibleToAnyNonMaintainerRole(page);
		}
	}

	private static final class BulkSecurityStrategy implements SitePermissionStrategy {

		private final Set<String> siteUpdaterRefs;
		private final Set<String> instructorRefs;

		private BulkSecurityStrategy(Set<String> siteUpdaterRefs, Set<String> instructorRefs) {
			this.siteUpdaterRefs = siteUpdaterRefs;
			this.instructorRefs = instructorRefs;
		}

		@Override
		public boolean canUpdate(Site site, SecurityService securityService) {
			return siteUpdaterRefs.contains(site.getReference());
		}

		@Override
		public boolean isInstructor(Site site, SecurityService securityService) {
			return instructorRefs.contains(site.getReference());
		}

		@Override
		public boolean isFirstToolVisibleToAnyNonMaintainerRole(Site site, SitePage page, ToolManager toolManager) {
			return toolManager.isFirstToolVisibleToAnyNonMaintainerRole(page);
		}
	}

	private static final class BulkRoleFunctionsStrategy implements SitePermissionStrategy {

		private final Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef;

		private BulkRoleFunctionsStrategy(Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef) {
			this.nonMaintainerRoleFunctionsBySiteRef = nonMaintainerRoleFunctionsBySiteRef;
		}

		@Override
		public boolean canUpdate(Site site, SecurityService securityService) {
			return canUpdateViaSecurity(site, securityService);
		}

		@Override
		public boolean isInstructor(Site site, SecurityService securityService) {
			return isInstructorViaSecurity(site, securityService);
		}

		@Override
		public boolean isFirstToolVisibleToAnyNonMaintainerRole(Site site, SitePage page, ToolManager toolManager) {
			return matchesBulkRoleFunctions(site, page, toolManager, nonMaintainerRoleFunctionsBySiteRef);
		}
	}

	private static final class BulkAllStrategy implements SitePermissionStrategy {

		private final Set<String> siteUpdaterRefs;
		private final Set<String> instructorRefs;
		private final Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef;

		private BulkAllStrategy(Set<String> siteUpdaterRefs, Set<String> instructorRefs,
				Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef) {
			this.siteUpdaterRefs = siteUpdaterRefs;
			this.instructorRefs = instructorRefs;
			this.nonMaintainerRoleFunctionsBySiteRef = nonMaintainerRoleFunctionsBySiteRef;
		}

		@Override
		public boolean canUpdate(Site site, SecurityService securityService) {
			return siteUpdaterRefs.contains(site.getReference());
		}

		@Override
		public boolean isInstructor(Site site, SecurityService securityService) {
			return instructorRefs.contains(site.getReference());
		}

		@Override
		public boolean isFirstToolVisibleToAnyNonMaintainerRole(Site site, SitePage page, ToolManager toolManager) {
			return matchesBulkRoleFunctions(site, page, toolManager, nonMaintainerRoleFunctionsBySiteRef);
		}
	}

	private static boolean matchesBulkRoleFunctions(Site site, SitePage page, ToolManager toolManager,
			Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef) {
		List<ToolConfiguration> pageTools = page.getTools();
		List<Set<String>> requiredPermissions = pageTools.size() == 1
				? toolManager.getRequiredPermissions(pageTools.get(0))
				: Collections.emptyList();

		if (requiredPermissions.isEmpty()) {
			return true;
		}

		Collection<Set<String>> nonMaintainerRoleFunctions = nonMaintainerRoleFunctionsBySiteRef.getOrDefault(
				site.getReference(), Collections.emptyList());

		for (Set<String> permissionSet : requiredPermissions) {
			for (Set<String> roleFunctions : nonMaintainerRoleFunctions) {
				if (roleFunctions.containsAll(permissionSet)) {
					return true;
				}
			}
		}

		return false;
	}
}
