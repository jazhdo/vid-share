# VidShare

## Description

This is a website for sharing videos. Just that, no ads, no payments, no whatever.

## Components

### SQLITE Database
> A few tables containing information on users, likes, shares, videos, followers, and comments

### Video loading
> Simple mp4 bytes

### Backend 
> Spring Boot 

### Auth
> A custom made auth using bcrypt for passwords and the "users" sqlite database for storing them

### Secure Variables
> A Dotenv loader imported from elsewhere

### Continued Auth State
> JSON Web Tokens from a external dependency

### Webpage Formatting
> Thymeleaf for changing fields depending on the page, ex. homepage, video watching page, search page, etc.

### Image conversion (for profile pictures)
> Scrimage webp conversion (an external dependency)