# The site hub's relay as a container: CI serves it as https://hub.kiel.party with a test cert
# (web/tests/site-hub.e2e.ts). Mount the cert and key at /certs.
FROM golang:1.25-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN go vet ./... && go test ./... && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /hub .

FROM scratch
COPY --from=build /hub /hub
EXPOSE 443
ENTRYPOINT ["/hub"]
