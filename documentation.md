# Documentation

## Required Environment Variables

- JWT_KEY: A key for use in JSON web tokens, use a cryptographically secure string of UTF-8 that has to be at least 32 bytes (Basic alphanumeric characters and simple symbols are all 1 byte each)
- CLOUDFLARE_ACCOUNT_ID: The account ID of the account with the Cloudflare D1 database on
- CLOUDFLARE_DB_ID: The UUID of the Cloudflare D1 database to use (Not the database name, the UUID)
- CLOUDFLARE_API_TOKEN: The Cloudflare API token to use the Cloudflare D1 database

## Optional Environment Variables

- DEFAULT_PFP: Path to the default pfp, relative to the project's root, defaults to "cat.png"
- DB_PATH: The path to the database file (.db file extension included), defaults to "database.db"
- VIDEO_PATH: The path of the folder to store the videos in, defaults to "video"