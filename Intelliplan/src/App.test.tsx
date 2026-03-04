import { render, screen } from '@testing-library/react';
import App from './App';

test('renders chat title and send button', () => {
  render(<App />);
  expect(screen.getByText(/intelliplan chat/i)).toBeInTheDocument();
  expect(
    screen.getByRole('button', {
      name: /skicka/i
    })
  ).toBeInTheDocument();
});
