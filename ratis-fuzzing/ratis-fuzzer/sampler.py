import random

if __name__ == '__main__':
    while True:
        size = int(input('Sample size: '))
        options = int(input('Number of options:'))

        dataset = [random.randint(1, options) for _ in range(size)]
        num_options = []
        for option in range(1, options+1, 1):
            num_options.append(dataset.count(option))

        print(f'Option selection counts: {num_options}')