package com.sleepsound.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.sleepsound.audio.SoundId
import com.sleepsound.audio.SoundTier
import com.sleepsound.audio.productId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BillingManager"

/**
 * Wraps Play Billing v8 for one-time per-sound IAPs. Lifecycle:
 *   1. init() at app start — registers the listener and starts a connection.
 *   2. connect() — opens the BillingClient (auto-reconnects on disconnect).
 *   3. queryProducts() — fetches ProductDetails for every premium SoundId,
 *      caches the localized price string for UI ("Buy $0.99").
 *   4. queryExistingPurchases() — syncs EntitlementStore with the user's
 *      Play account; run at startup and after purchase.
 *   5. launchPurchaseFlow(activity, id) — opens the Play purchase sheet.
 *   6. The PurchasesUpdatedListener processes results; successful purchases
 *      are acknowledged and routed into EntitlementStore.
 *
 * All operations are no-ops on devices without Google Play Services or when
 * the BillingClient fails to connect — premium sounds stay locked and the
 * free tier continues to work.
 */
object BillingManager {

    private var billingClient: BillingClient? = null
    private var appScope: CoroutineScope? = null
    private val productCache = mutableMapOf<String, ProductDetails>()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Localized price strings keyed by SoundId, e.g. mapOf(PINK_NOISE to "$0.99"). */
    private val _prices = MutableStateFlow<Map<SoundId, String>>(emptyMap())
    val prices: StateFlow<Map<SoundId, String>> = _prices.asStateFlow()

    /** Most recent purchase event for the UI to surface. Cleared by [consumeLastResult]. */
    private val _lastResult = MutableStateFlow<PurchaseResult?>(null)
    val lastResult: StateFlow<PurchaseResult?> = _lastResult.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val scope = appScope ?: return@PurchasesUpdatedListener
        scope.launch {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases?.forEach { handlePurchase(it) }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    _lastResult.value = PurchaseResult.UserCanceled
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    // Already-owned typically means we missed an ack from a prior session.
                    queryExistingPurchases()
                }
                else -> {
                    _lastResult.value = PurchaseResult.Failure(
                        reason = "${billingResult.responseCode}: ${billingResult.debugMessage}",
                    )
                }
            }
        }
    }

    fun init(context: Context, scope: CoroutineScope) {
        if (billingClient != null) return
        appScope = scope
        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    fun connect() {
        val client = billingClient ?: return
        if (client.isReady) {
            _connectionState.value = ConnectionState.CONNECTED
            refreshAfterConnect()
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = ConnectionState.CONNECTED
                    refreshAfterConnect()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    _connectionState.value = ConnectionState.ERROR
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    private fun refreshAfterConnect() {
        val scope = appScope ?: return
        scope.launch {
            queryProducts()
            queryExistingPurchases()
        }
    }

    suspend fun queryProducts() {
        val client = billingClient?.takeIf { it.isReady } ?: return
        val products = SoundId.entries
            .filter { it.tier == SoundTier.PREMIUM }
            .mapNotNull { id ->
                id.productId()?.let {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            }
        if (products.isEmpty()) return

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        val response = withContext(Dispatchers.IO) {
            client.queryProductDetails(params)
        }
        if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetails failed: ${response.billingResult.debugMessage}")
            return
        }
        val priceMap = mutableMapOf<SoundId, String>()
        response.productDetailsList?.forEach { details ->
            productCache[details.productId] = details
            val id = SoundId.entries.firstOrNull { it.productId() == details.productId } ?: return@forEach
            details.oneTimePurchaseOfferDetails?.formattedPrice?.let {
                priceMap[id] = it
            }
        }
        _prices.value = priceMap
    }

    suspend fun queryExistingPurchases() {
        val client = billingClient?.takeIf { it.isReady } ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val response = withContext(Dispatchers.IO) {
            client.queryPurchasesAsync(params)
        }
        if (response.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryPurchases failed: ${response.billingResult.debugMessage}")
            return
        }
        val owned = response.purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .mapNotNull { pid -> SoundId.entries.firstOrNull { it.productId() == pid } }
            .toSet()
        EntitlementStore.setPaidUnlocks(owned)

        // Acknowledge any purchases we may have missed.
        response.purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { handlePurchase(it) }
    }

    /**
     * Open the Play purchase sheet for the given premium sound. Returns false
     * if the product is unknown (not yet fetched / not registered in Play
     * Console) so the caller can show an error.
     */
    fun launchPurchaseFlow(activity: Activity, id: SoundId): Boolean {
        if (id.tier == SoundTier.FREE) return false
        val client = billingClient?.takeIf { it.isReady } ?: return false
        val productId = id.productId() ?: return false
        val details = productCache[productId] ?: return false

        val productParams = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build(),
        )
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productParams)
            .build()
        val result = client.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    suspend fun restorePurchases(): RestoreResult {
        val client = billingClient?.takeIf { it.isReady }
            ?: return RestoreResult(0, "Play Billing not available")
        val before = EntitlementStore.unlocked.value
            .filter { it.tier == SoundTier.PREMIUM }
            .toSet()
        queryExistingPurchases()
        val after = EntitlementStore.unlocked.value
            .filter { it.tier == SoundTier.PREMIUM }
            .toSet()
        return RestoreResult(restoredCount = (after - before).size)
    }

    fun consumeLastResult() {
        _lastResult.value = null
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val client = billingClient ?: return

        val ids = purchase.products
            .mapNotNull { pid -> SoundId.entries.firstOrNull { it.productId() == pid } }
        ids.forEach { EntitlementStore.unlock(it) }

        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val ackResult = withContext(Dispatchers.IO) {
                client.acknowledgePurchase(ackParams)
            }
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${ackResult.debugMessage}")
            }
        }
        ids.firstOrNull()?.let { _lastResult.value = PurchaseResult.Success(it) }
    }
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

sealed class PurchaseResult {
    data class Success(val id: SoundId) : PurchaseResult()
    data object UserCanceled : PurchaseResult()
    data class Failure(val reason: String) : PurchaseResult()
}

data class RestoreResult(val restoredCount: Int, val error: String? = null)
