# VidShare

## Description

This is a website for sharing videos. Just that, no ads, no payments, no whatever.

## Components

### SQLITE Database
> A few tables containing information on users, likes, shares, videos, followers, and comments

### Video loading
> Simple mp4 bytes

### Backend 
> Spring Boot Java

### Auth
> A custom made auth using bcrypt for passwords and the "users" sqlite database for storing them

### Secure Variables
> Environment Variables

### Continued Auth State
> JSON Web Tokens from a external dependency

### Webpage Formatting
> Thymeleaf for changing fields depending on the page, ex. homepage, video watching page, search page, etc.

### Image conversion (for profile pictures)
> Scrimage webp conversion (an external dependency)

## Supported sizes
> Width: Minimum of 800px
> Height: Minimum of 700px

## Tested devices
> Firefox 158.0 Build #20261007201254 on macOS 15.7.3

## Links (because a reviewer wanted to see them here)
1. [Link 1](https://video.itsjaz.com) (Works probably)
2. [Link 2](https://vid-share-new.onrender.com) (Always works)
3. [Link 3](http://video.itsjaz.com:8080) (May be broken)