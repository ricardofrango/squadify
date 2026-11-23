-- Subscriptions bought in the stores (REQUIREMENTS §5c, payments; Google Play first): one row a purchase
-- token, as the store last told the server. The space's plan follows the ones that give it now.
CREATE TABLE store_subscriptions (
    purchase_token VARCHAR(512) PRIMARY KEY,
    club_id UUID NOT NULL,
    user_id UUID NOT NULL,
    store VARCHAR(10) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    base_plan_id VARCHAR(100),
    plan VARCHAR(20) NOT NULL,
    entitled BOOLEAN NOT NULL,
    expires_at TIMESTAMP,
    pending_plan VARCHAR(20),
    test BOOLEAN NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_store_subscriptions_club_id__id FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_store_subscriptions_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
