import { createSignerHandler } from "./common";

export default createSignerHandler("timestamp", "SEEN_TIMESTAMP_SIGNING_KEY");
