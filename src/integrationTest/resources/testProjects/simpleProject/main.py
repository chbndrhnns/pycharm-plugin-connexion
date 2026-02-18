def process_order(customer_name: str, item_count: int, total_price: float, discount: float):
    final_price = total_price * (1 - discount)
    return f"Order for {customer_name}: {item_count} items, total: {final_price}"


process_order("Alice", 3, 99.99, 0.1)
