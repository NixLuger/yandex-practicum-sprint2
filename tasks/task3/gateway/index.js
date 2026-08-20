import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { ApolloGateway } from '@apollo/gateway';
import { ApolloGateway, RemoteGraphQLDataSource } from '@apollo/gateway';
import { IntrospectAndCompose } from '@apollo/gateway';

// Кастомный DataSource, который добавляет заголовки из контекста
class AuthenticatedDataSource extends RemoteGraphQLDataSource {
  willSendRequest({ request, context }) {
    // Если в контексте есть userid, добавляем его в заголовки
    if (context.userid) {
      request.http.headers.set('userid', context.userid);
    }
  }
}

const gateway = new ApolloGateway({
  // Используем IntrospectAndCompose вместо устаревшего serviceList
  supergraphSdl: new IntrospectAndCompose({
    subgraphs: [
      { name: 'booking', url: 'http://booking-subgraph:4001' },
      { name: 'hotel', url: 'http://hotel-subgraph:4002' },
    ],
  }),
  // Используем кастомный DataSource для передачи заголовков
  buildService({ url }) {
    return new AuthenticatedDataSource({ url });
  },
});

const server = new ApolloServer({ gateway, introspection: true });

startStandaloneServer(server, {
  listen: { port: 4000 },
  context: async ({ req }) => {
    const userid = req.headers['userid'] || null;
    return { userid };
  },
}).then(() => {
  console.log('🚀 Gateway ready at http://localhost:4000');
});