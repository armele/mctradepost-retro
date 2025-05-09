package com.deathfrog.mctradepost.gui;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.client.gui.AbstractWindowRequestTree;
import com.minecolonies.core.network.messages.server.colony.UpdateRequestStateMessage;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;

import com.google.common.collect.Lists;
import com.minecolonies.api.util.Log;
import org.jetbrains.annotations.NotNull;
import java.util.*;

import static com.minecolonies.api.util.constant.WindowConstants.CLIPBOARD_TOGGLE;

public class AdvancedWindowClipBoard extends AbstractWindowRequestTree {
    public static final String ADVANCED_CLIPBOARD_TOGGLE = "playeronly";

    /**
     * Resource suffix.
     */
    private static final String BUILD_TOOL_RESOURCE_SUFFIX = ":gui/advancedwindowclipboard.xml";

    /**
     * List of async request tokens.
     */
    private final List<IToken<?>> asyncRequest = new ArrayList<>();

    /**
     * The colony id.
     */
    private final IColonyView colony;

    /**
     * Hide or show not important requests.
     */
    private boolean hide = false;

    /* Show only requests to be fulfilled by the player. */
    private boolean playerOnly = false;

    public AdvancedWindowClipBoard(IColonyView colony)
    {
        super(null, MCTradePostMod.MODID + BUILD_TOOL_RESOURCE_SUFFIX, colony);
        this.colony = colony;
        for (final ICitizenDataView view : this.colony.getCitizens().values())
        {
            if (view.getJobView() != null)
            {
                asyncRequest.addAll(view.getJobView().getAsyncRequests());
            }
        }
        registerButton(CLIPBOARD_TOGGLE, this::toggleImportant);
        registerButton(ADVANCED_CLIPBOARD_TOGGLE, this::togglePlayerOnly);
    }

    private void toggleImportant()
    {
        this.hide = !this.hide;
    }

    private void togglePlayerOnly()
    {
        this.playerOnly = !this.playerOnly;
    }

    /**
     * Check if the given request is a player request.
     * 
     * @param request the request to check
     * @return true if the request is a player request, false otherwise
     */
    protected boolean isPlayerRequest(final IRequest<?> request)
    {
        boolean isPlayer = false;

        try
        {
            final IRequestResolver<?> resolver = colony.getRequestManager().getResolverForRequest(request.getId());
            if (resolver == null)
            {
                Log.getLogger().warn("---IRequestResolver Null in AdvancedWindowClipBoard---");
                return false;
            }

            String resolverName = resolver.getRequesterDisplayName(colony.getRequestManager(), request).getString();
            if (resolverName.equals("Player"))  {
                isPlayer = true;
            }
        }
        catch (Exception e)
        {
            /*
             * Do nothing we just need to know if it has a resolver or not.
             */
            Log.getLogger().warn("---Exception in AdvancedWindowClipBoard---", e);
        }

        return isPlayer;
    }

    protected void addToFilteredRequests(IRequestManager manager, ArrayList<IRequest<?>> filteredRequests, IRequest<?> request)
    {
        if (isPlayerRequest(request)) {
            filteredRequests.add(request);
        }

        if (request.hasChildren()) {
            
            for (final Object o : request.getChildren())
            {
                if (o instanceof IToken<?>)
                {
                    final IToken<?> iToken = (IToken<?>) o;
                    final IRequest<?> childRequest = manager.getRequestForToken(iToken);

                    if (childRequest != null)
                    {
                        addToFilteredRequests(manager, filteredRequests, childRequest);
                    }
                }
            }
        }
    }

    protected ArrayList<IRequest<?>> filterRequests(IRequestManager manager, IBuildingView buildingView, ArrayList<IRequest<?>> requests)
    {
        final ArrayList<IRequest<?>> filteredRequests = Lists.newArrayList();

        requests.forEach(request -> {
            addToFilteredRequests(manager, filteredRequests, request);
        });

        return filteredRequests;
    }

    @Override
    public ImmutableList<IRequest<?>> getOpenRequestsFromBuilding(final IBuildingView building)
    {
        final ArrayList<IRequest<?>> requests = Lists.newArrayList();

        if (colony == null)
        {
            return ImmutableList.of();
        }

        final IRequestManager requestManager = colony.getRequestManager();

        if (requestManager == null)
        {
            return ImmutableList.of();
        }

        try
        {
            final IPlayerRequestResolver resolver = requestManager.getPlayerResolver();
            final IRetryingRequestResolver retryingRequestResolver = requestManager.getRetryingRequestResolver();

            final Set<IToken<?>> requestTokens = new HashSet<>();
            requestTokens.addAll(resolver.getAllAssignedRequests());
            requestTokens.addAll(retryingRequestResolver.getAllAssignedRequests());

            for (final IToken<?> token : requestTokens)
            {
                IRequest<?> request = requestManager.getRequestForToken(token);

                while (request != null && request.hasParent())
                {
                    request = requestManager.getRequestForToken(request.getParent());
                }

                //TOTEST: Use toggle setting and integrate with isPlayer  
                if (request != null && !requests.contains(request))
                {
                    requests.add(request);
                }
            }

            if (hide)
            {
                requests.removeIf(req -> asyncRequest.contains(req.getId()));
            }

            // Advanced clipboard functionality - only show requests to be fulfilled by the player
            if (playerOnly) {
                final ArrayList<IRequest<?>> filteredRequests = filterRequests(requestManager, building, requests);
                requests.clear();
                requests.addAll(filteredRequests);
            }

            final BlockPos playerPos = Minecraft.getInstance().player.blockPosition();
            requests.sort(Comparator.comparing((IRequest<?> request) -> request.getRequester().getLocation().getInDimensionLocation()
                    .distSqr(new Vec3i(playerPos.getX(), playerPos.getY(), playerPos.getZ())))
                .thenComparingInt((IRequest<?> request) -> request.getId().hashCode()));
        }
        catch (Exception e)
        {
            Log.getLogger().warn("Exception trying to retreive requests:", e);
            requestManager.reset();
            return ImmutableList.of();
        }

        return ImmutableList.copyOf(requests);
    }

    @Override
    public boolean fulfillable(final IRequest<?> tRequest)
    {
        return false;
    }

    @Override
    protected void cancel(@NotNull final IRequest<?> request)
    {
        new UpdateRequestStateMessage(colony, request.getId(), RequestState.CANCELLED, null).sendToServer();
    }

}
