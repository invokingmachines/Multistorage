package com.invokingmachines.multistorage.sample.multitenancy;

import com.invokingmachines.multistorage.config.MultistorageWebProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class TenantPathFilter extends OncePerRequestFilter {

    private final MultistorageWebProperties webProperties;
    private final TenantSchemaRegistry tenantSchemaRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        String prefix = normalizePrefix(webProperties.getApiTenantPrefix());

        if (!path.startsWith(prefix)) {
            filterChain.doFilter(request, response);
            return;
        }

        String remainder = path.substring(prefix.length());
        if (remainder.startsWith("/")) {
            remainder = remainder.substring(1);
        }

        int slash = remainder.indexOf('/');
        String first = slash < 0 ? remainder : remainder.substring(0, slash);

        if (first.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("tenants".equals(first)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        if (!tenantSchemaRegistry.hasTenant(first)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            TenantContext.setTenantCode(first);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "/multistorage/api";
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}
