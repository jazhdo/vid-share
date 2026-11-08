# Documentation

## Required Environment Variables

- JWT_KEY: A key for use in JSON web tokens, use a cryptographically secure string of UTF-8 that has to be at least 32 bytes (Basic alphanumeric characters and simple symbols are all 1 byte each)

## Optional Environment Variables

- DEFAULT_PFP: Path to the default pfp, relative to the project's root, defaults to "cat.png"
- DB_PATH: The path to the database file (.db file extension included), defaults to "database.db"
- VIDEO_PATH: The path of the folder to store the videos in, defaults to "video"