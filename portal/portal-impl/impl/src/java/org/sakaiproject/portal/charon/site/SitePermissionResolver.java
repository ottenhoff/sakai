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
 * Preloaded site permissions for portal site-map rendering.
 *
 * <p>Portal navigation touches the same sites many times per request. This class loads
 * authz data once per batch, then answers repeated lookups from in-memory sets instead
 * of calling {@link SecurityService#unlock} or walking site roles for every page.
 *
 * <p>Two factory methods match the two batch shapes portal needs:
 * <ul>
 *   <li>{@link #forSecurityChecks} — site maintainer and instructor checks only</li>
 *   <li>{@link #forPageLocks} — those checks plus role functions for locked-page display</li>
 * </ul>
 *
 * <p>When bulk mode is off for a check, that method falls back to the live service path
 * (used for single-site rendering and role-swapped sessions).
 */
public final class SitePermissionResolver {

	private static final String INSTRUCTOR_FUNCTION = "section.role.instructor";

	/** Live authz per lookup; used when pages are omitted or only one site is rendered. */
	public static final SitePermissionResolver DIRECT = create(false, false,
			Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());

	/** Batched resolver with no sites; security checks deny, page locks see no roles. */
	public static final SitePermissionResolver NO_SITES = create(true, true,
			Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());

	/** When true, canUpdateSite/isInstructor use preloaded site ref sets instead of unlock. */
	private final boolean bulkSecurity;
	/** When true, page-lock checks use preloaded role functions instead of ToolManager. */
	private final boolean bulkPageLocks;
	private final Set<String> siteUpdaterRefs;
	private final Set<String> instructorRefs;
	private final Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef;

	private SitePermissionResolver(boolean bulkSecurity, boolean bulkPageLocks,
			Set<String> siteUpdaterRefs, Set<String> instructorRefs,
			Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef) {
		this.bulkSecurity = bulkSecurity;
		this.bulkPageLocks = bulkPageLocks;
		this.siteUpdaterRefs = siteUpdaterRefs;
		this.instructorRefs = instructorRefs;
		this.nonMaintainerRoleFunctionsBySiteRef = nonMaintainerRoleFunctionsBySiteRef;
	}

	/** Whether the current user may update the site (site maintainer). */
	public boolean canUpdateSite(Site site, SecurityService securityService) {
		if (!bulkSecurity) {
			return securityService.unlock(SiteService.SECURE_UPDATE_SITE, site.getReference());
		}
		return siteUpdaterRefs.contains(site.getReference());
	}

	/** Whether the current user has the instructor role in the site. */
	public boolean isInstructor(Site site, SecurityService securityService) {
		if (!bulkSecurity) {
			return securityService.unlock(INSTRUCTOR_FUNCTION, site.getReference());
		}
		return instructorRefs.contains(site.getReference());
	}

	/**
	 * Whether any non-maintainer role can see the first tool on a page.
	 * Used to mark pages as locked in the nav when students cannot access them.
	 */
	public boolean isFirstToolVisibleToAnyNonMaintainerRole(Site site, SitePage page, ToolManager toolManager) {
		if (!bulkPageLocks) {
			return toolManager.isFirstToolVisibleToAnyNonMaintainerRole(page);
		}
		return pageLockVisible(site, page, toolManager);
	}

	/**
	 * Batch resolver for gradebook visibility, page ordering, and similar security-only checks.
	 */
	public static SitePermissionResolver forSecurityChecks(Collection<Site> sites,
			AuthzGroupService authzGroupService, SecurityService securityService, SessionManager sessionManager) {
		return build(sites, false, authzGroupService, securityService, sessionManager);
	}

	/**
	 * Batch resolver for site-map rendering, including locked-page detection.
	 */
	public static SitePermissionResolver forPageLocks(Collection<Site> sites,
			AuthzGroupService authzGroupService, SecurityService securityService, SessionManager sessionManager) {
		return build(sites, true, authzGroupService, securityService, sessionManager);
	}

	private static SitePermissionResolver build(Collection<Site> sites, boolean withPageLockData,
			AuthzGroupService authzGroupService, SecurityService securityService, SessionManager sessionManager) {

		List<String> siteRefs = distinctSiteRefs(sites);
		if (siteRefs.isEmpty()) {
			return NO_SITES;
		}

		Map<String, Collection<Set<String>>> nonMaintainerRoleFunctions = withPageLockData
				? nonMaintainerRoleFunctions(authzGroupService.getRoleFunctions(siteRefs))
				: Collections.emptyMap();

		if (securityService.isUserRoleSwapped()) {
			// Bulk user checks reflect the swapped role, not the real session user.
			return create(false, withPageLockData, Collections.emptySet(), Collections.emptySet(),
					nonMaintainerRoleFunctions);
		}

		return createForSessionUser(sessionManager.getCurrentSessionUserId(), siteRefs, withPageLockData,
				nonMaintainerRoleFunctions, authzGroupService, securityService);
	}

	private static SitePermissionResolver createForSessionUser(String userId, List<String> siteRefs,
			boolean withPageLockData, Map<String, Collection<Set<String>>> nonMaintainerRoleFunctions,
			AuthzGroupService authzGroupService, SecurityService securityService) {

		if (StringUtils.isBlank(userId)) {
			return create(true, withPageLockData, Collections.emptySet(), Collections.emptySet(),
					nonMaintainerRoleFunctions);
		}

		if (securityService.isSuperUser()) {
			Set<String> allSites = new HashSet<>(siteRefs);
			return create(true, withPageLockData, allSites, allSites, nonMaintainerRoleFunctions);
		}

		Set<String> siteUpdaterRefs = authzGroupService.getAuthzGroupsIsAllowed(userId,
				SiteService.SECURE_UPDATE_SITE, siteRefs);
		Set<String> instructorRefs = authzGroupService.getAuthzGroupsIsAllowed(userId,
				INSTRUCTOR_FUNCTION, siteRefs);
		return create(true, withPageLockData, siteUpdaterRefs, instructorRefs, nonMaintainerRoleFunctions);
	}

	private static SitePermissionResolver create(boolean bulkSecurity, boolean bulkPageLocks,
			Set<String> siteUpdaterRefs, Set<String> instructorRefs,
			Map<String, Collection<Set<String>>> nonMaintainerRoleFunctionsBySiteRef) {
		return new SitePermissionResolver(bulkSecurity, bulkPageLocks, siteUpdaterRefs, instructorRefs,
				nonMaintainerRoleFunctionsBySiteRef);
	}

	private static List<String> distinctSiteRefs(Collection<Site> sites) {
		if (sites == null || sites.isEmpty()) {
			return Collections.emptyList();
		}
		return sites.stream()
				.filter(Objects::nonNull)
				.map(Site::getReference)
				.distinct()
				.collect(Collectors.toList());
	}

	/** Strip maintainer roles; portal page-lock logic only considers everyone else. */
	private static Map<String, Collection<Set<String>>> nonMaintainerRoleFunctions(
			Map<String, Map<String, Set<String>>> roleFunctionsBySiteRef) {
		if (roleFunctionsBySiteRef == null || roleFunctionsBySiteRef.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<String, Collection<Set<String>>> result = new HashMap<>();
		for (Map.Entry<String, Map<String, Set<String>>> entry : roleFunctionsBySiteRef.entrySet()) {
			Map<String, Set<String>> roleFunctions = entry.getValue();
			if (roleFunctions == null || roleFunctions.isEmpty()) {
				result.put(entry.getKey(), Collections.emptyList());
				continue;
			}
			List<Set<String>> nonMaintainer = roleFunctions.values().stream()
					.filter(functions -> !functions.contains(SiteService.SECURE_UPDATE_SITE))
					.collect(Collectors.toList());
			result.put(entry.getKey(), nonMaintainer);
		}
		return result;
	}

	/**
	 * Bulk equivalent of {@link ToolManager#isFirstToolVisibleToAnyNonMaintainerRole(SitePage)}.
	 */
	private boolean pageLockVisible(Site site, SitePage page, ToolManager toolManager) {
		List<ToolConfiguration> pageTools = page.getTools();
		if (pageTools.size() != 1) {
			return true;
		}

		List<Set<String>> requiredPermissions = toolManager.getRequiredPermissions(pageTools.get(0));
		if (requiredPermissions.isEmpty()) {
			return true;
		}

		Collection<Set<String>> roleFunctions = nonMaintainerRoleFunctionsBySiteRef.getOrDefault(
				site.getReference(), Collections.emptyList());
		for (Set<String> required : requiredPermissions) {
			for (Set<String> allowed : roleFunctions) {
				if (allowed.containsAll(required)) {
					return true;
				}
			}
		}
		return false;
	}
}
