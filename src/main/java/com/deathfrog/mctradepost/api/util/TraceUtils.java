package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class TraceUtils {

    public static final String TRACE_NONE =             "none";
    public static final String TRACE_BURNOUT =          "burnout";
    public static final String TRACE_SHOPKEEPER =       "shopkeeper";
    public static final String TRACE_SHOPPER =          "shopper";
    public static final String TRACE_RECYCLING =        "recycling";
    public static final String TRACE_RECYCLING_RECIPE = "recyclingrecipe";
    public static final String TRACE_STATION =          "station";
    public static final String TRACE_GUESTSERVICES =    "guestservices";
    public static final String TRACE_ANIMALTRAINER =    "animaltrainer";

    // Static setting to control whether we should execute the logging
    private static final Map<String, Boolean> TRACE_MAP = new HashMap<>();

    public static final Logger LOGGER = LogUtils.getLogger();


    /**
     * Allows for dynamic tracing control at runtime.
     * If the trace key has been set to true, the logging statement will be executed.
     * Otherwise, the method call does nothing.
     * @param traceKey the key to check in the TRACE_MAP
     * @param loggingStatement the code to execute if trace is turned on
     */
    public static void dynamicTrace(String traceKey, Runnable loggingStatement) {
        if (TRACE_MAP.containsKey(traceKey) && TRACE_MAP.get(traceKey)) {
            loggingStatement.run();
        }
    }

    /**
     * Allows for dynamic tracing control at runtime.
     * Sets the trace key to either true or false in the TRACE_MAP.
     * @param traceKey the key to set in the TRACE_MAP
     * @param traceSetting the value to set the key to
     */
    public static void setTrace(String traceKey, boolean traceSetting)
    {
        TRACE_MAP.put(traceKey, traceSetting);
    }

    /**
     * Retrieves a list of trace keys known to have
     * dynamic tracing associated with them.
     *
     * @return a list of strings representing the trace keys.
     */

    public static List<String> getTraceKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(TRACE_NONE);
        keys.add(TRACE_BURNOUT);
        keys.add(TRACE_SHOPKEEPER);
        keys.add(TRACE_SHOPPER);
        keys.add(TRACE_RECYCLING);
        keys.add(TRACE_RECYCLING_RECIPE);
        keys.add(TRACE_STATION);
        keys.add(TRACE_GUESTSERVICES);
        keys.add(TRACE_ANIMALTRAINER);

        return keys;
    }
}
