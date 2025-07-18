# Stage 1: build with the full .NET SDK
FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src

# Copy csproj and restore dependencies (leverages layer caching)
COPY *.csproj ./
RUN dotnet restore

# Copy everything else and publish
COPY *.cs ./
RUN dotnet publish -c Release -o /app/publish /p:PublishTrimmed=true

# Stage 2: run on the lean runtime image
FROM mcr.microsoft.com/dotnet/runtime:9.0
WORKDIR /app

# Copy the published output from the build stage
COPY --from=build /app/publish ./

# If your project produces an EXE instead of a DLL, adjust accordingly:
# ENTRYPOINT ["./MyBot"]
ENTRYPOINT ["dotnet", "MyBot.dll"]
