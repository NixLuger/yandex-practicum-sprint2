import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import { listBookings } from './grpcClient.js';
import gql from 'graphql-tag';

const typeDefs = gql`
  type Hotel @key(fields: "id") {
    id: ID!
  }

  type Booking @key(fields: "id") {
    id: ID!
    userId: String!
    hotelId: String!
    hotel: Hotel
    promoCode: String
    discountPercent: Int
  }

  type Query {
    bookingsByUser(userId: String!): [Booking]
  }

`;

const resolvers = {
    Query: {
        bookingsByUser: async (_, { userId }, { userIdFromHeader }) => {
          if (!userIdFromHeader)
            throw new Error('Unauthorized: missing userid header');
          if (userIdFromHeader !== userId)
            throw new Error('Forbidden: you can only view your own bookings');

          try {
            const response = await listBookings({ userId });
            console.log(response);
            return response.getBookingsList().map((booking) => ({
              id: booking.getId(),
              userId: booking.getUserId(),
              hotelId: booking.getHotelId(),
              promoCode: booking.getPromoCode(),
              discountPercent: booking.getDiscountPercent(),
            }));
          } catch (err) {
            console.error('gRPC error:', err);
            throw new Error('Failed to fetch bookings from gRPC service');
          }
        },
    },
  Booking: {

    // указываю поле, которое является ключём для разрешения hotel через тип Hotel
    hotel: (parent) => ({__typename: 'Hotel', id: parent.hotelId}),

    // тут не делаю запрос к api т.к. в задании небыло необходимости резолвить букинги, да и сервис на это не заточен ещё
    __resolveReference: async (reference) => { return { id: reference.id }}
  }

};

const server = new ApolloServer({
  schema: buildSubgraphSchema([{ typeDefs, resolvers }]),
});

startStandaloneServer(server, {
  listen: { port: 4001 },
    context: async ({ req }) => {
      const userIdFromHeader = req.headers['userid'] || null; // авторизация на основании простой строки в хидере запроса (по заданию)
      return { userIdFromHeader };
    },
}).then(() => {
  console.log('✅ Booking subgraph ready at http://localhost:4001/');
});