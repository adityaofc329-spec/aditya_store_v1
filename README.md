# ADI_STORE_V1 — Customer + Admin Android App

This version wires the Android client to the Supabase project and implements the main manual-payment flow:

**Login/Signup → Store → UPI/QR → Create Order → UTR + optional screenshot → UNDER_REVIEW → Admin APPROVE/REJECT → approved delivery**

## Included
- Supabase Auth email/password
- Customer store and product list
- UPI ID + locally generated QR
- Open installed UPI app
- Order creation
- UTR/manual payment submission
- Private payment screenshot upload to `payment-proofs`
- Customer order/status screen
- Approved-only delivery retrieval through `get_approved_delivery`
- Admin role check from `profiles`
- Admin under-review queue
- Admin UTR/proof access, approve and reject actions
- Basic product add/enable/disable management

The Android client uses the Supabase publishable key only. Supabase's Kotlin documentation describes the Auth, PostgREST and Storage client modules and recommends a publishable key for client initialization; secret/service-role keys must stay server-side. See the official docs: https://supabase.com/docs/reference/kotlin/initializing

## Database assumptions
The SQL you already ran should exist, including:
- `profiles`, `products`, `orders`, `payments`, `payment_proofs`
- RLS policies
- private `payment-proofs` bucket
- RPCs: `submit_payment_for_review`, `approve_order`, `reject_order`, `get_approved_delivery`

## Important
1. In Supabase Auth, enable the email/password provider.
2. If email confirmation is enabled, users must confirm their email before login.
3. The app deliberately does not use a service-role key.
4. Delivery data should be treated as sensitive. For production, move sensitive delivery secrets out of the public product row into a protected order-delivery table or server-side function.
5. If this marketplace concerns game accounts/items, verify that the relevant game's Terms of Service permit the transaction model.

## Build
Open the project folder in Android Studio and run **Build > Make Project**. Then use **Build > Build APK(s)**.

A signed release APK still requires your own Android signing key/keystore. This environment does not have Android Studio/Gradle build tooling installed, so no successful APK build is claimed here.
