/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  return {
    __esModule: true,
    default: { View },
    useSharedValue: (value: number) => ({ value }),
    useAnimatedStyle: (style: () => object) => style(),
    withTiming: (value: number) => value,
  };
});
import App from '../App';

test('renders correctly', async () => {
  await ReactTestRenderer.act(() => {
    ReactTestRenderer.create(<App />);
  });
});
