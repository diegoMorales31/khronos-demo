package com.moga.khronosdemo.dtos.response;

/**
 * Stable reason sent as "code" in some error responses, so clients can tell these cases apart without
 * reading the message, whose wording may change. Responses for any other error leave the field out.
 * A 409 gets one, and the details that go with it, through BusinessRuleException; the 403 of a feature
 * outside the owner's plan through PlanFeatureUnavailableException.
 */
public enum ErrorCode {

    /** 401 at login: unknown username or wrong password, whatever the state of the account. */
    INVALID_CREDENTIALS,

    /** 401 at login with the right password, or on a request with a token, when the account is disabled. */
    ACCOUNT_DISABLED,

    /** 401 when the token has expired, was revoked by a password change or is otherwise not valid. */
    SESSION_EXPIRED,

    /** 401 when an endpoint that needs a token is called without one. */
    AUTHENTICATION_REQUIRED,

    /** 429 at login while the username and IP, or the IP alone, is blocked; Retry-After says for how long. */
    TOO_MANY_ATTEMPTS,

    /** 409 when the establishment already has a cash closing for that business day; details.date is the day. */
    CASH_CLOSING_EXISTS,

    /** 409 when a closed cash closing is edited, deleted or closed again. */
    CASH_CLOSING_CLOSED,

    /** 409 when a cash closing that is not closed is reopened. */
    CASH_CLOSING_NOT_CLOSED,

    /** 409 when a draft that was closed before, and then reopened, is deleted. */
    CASH_CLOSING_WAS_CLOSED,

    /** 409 when a day is closed with a cash difference over the threshold and no differenceReason. */
    DIFFERENCE_REASON_REQUIRED,

    /** 409 when a day is closed before its cash has been counted (countedCash is null). */
    CASH_NOT_COUNTED,

    /**
     * 409 when a cash expense is added, changed, moved or deleted on a day whose cash closing is closed,
     * also the one a purchase order's receipt would create; details.date is that day.
     */
    CASH_DAY_CLOSED,

    /**
     * 409 when a cash closing's business day, an expense's date, or the date of a stock movement, a stock
     * count or a purchase order's receipt is after today in the establishment's time zone.
     */
    FUTURE_DATE,

    /** 409 when another supplier of the establishment already has that name, whatever its case. */
    SUPPLIER_NAME_TAKEN,

    /** 409 when another product of the establishment already has that name, whatever its case. */
    PRODUCT_NAME_TAKEN,

    /** 409 when a product is inactive and so takes no new stock movements or purchase order lines. */
    PRODUCT_INACTIVE,

    /** 409 when a product's unit is changed after stock movements were recorded or purchase order lines added in it. */
    PRODUCT_UNIT_LOCKED,

    /** 409 when a product with stock movements or purchase order lines is deleted; deactivate it instead. */
    PRODUCT_HAS_HISTORY,

    /** 409 when a request names a supplier or product of another establishment than the one it is for. */
    OTHER_ESTABLISHMENT,

    /** 409 when a supplier with purchase orders is deleted. */
    SUPPLIER_HAS_ORDERS,

    /** 409 when a purchase order is edited, deleted, ordered, cancelled or received in a status that doesn't allow it. */
    PURCHASE_ORDER_WRONG_STATUS,

    /** 409 when a purchase order is received with nothing received on any line; cancel it instead. */
    PURCHASE_ORDER_NOTHING_RECEIVED,

    /** 409 when a purchase order is received with an expense but its total is unknown or 0. */
    PURCHASE_ORDER_NO_TOTAL,

    /**
     * 409 when an expense created by receiving a purchase order is edited; details.purchaseOrderId is that
     * order. Such an expense can only be deleted, and entered again by hand if it needs correcting.
     */
    EXPENSE_FROM_PURCHASE_ORDER,

    /**
     * 409 when a worker would be added or reactivated past the active workers the owner's plan allows,
     * or an owner is moved to a plan that allows fewer than they have; details.plan, details.maxActiveWorkers
     * and details.activeWorkers say which plan and how many.
     */
    WORKER_LIMIT_REACHED,

    /**
     * 403 when an owner calls an endpoint of a feature their plan does not include; details.feature is
     * CASH or INVENTORY and details.plan the owner's plan.
     */
    PLAN_FEATURE_UNAVAILABLE
}
