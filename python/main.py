#!/usr/bin/env python3
import argparse

def generate(node_count: int, container_port: int = 8080, base_port: int = 1010):
    print("services:")
    
    for i in range(1, node_count + 1):
        service = f"node{i}"
        host_port = i * base_port
        
        print(f"  {service}:")
        print("    image: boutade-server")
        print("    build:")
        print("      dockerfile: Dockerfile")
        print("      context: .")
        print(f"    container_name: boutade_{service}")
        print("    networks:")
        print("      - boutade_network")
        print("    ports:")
        print(f"      - {host_port}:{container_port}")
        print("    environment:")
        print(f"      - CLUSTER_NODE_ID=node-{i}")
        print(f"      - CLUSTER_FAILURE_TIMEOUT=2000")

        # Only non-seed nodes need the seed in their members list
        if i > 1:
            print("      - CLUSTER_MEMBERS_0=node-1,node1,8080")

        print()

    print("networks:")
    print("  boutade_network:")
    print("    driver: bridge")
    print("    name: boutade_network")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate Boutade cluster docker-compose")
    parser.add_argument(
        "nodes", 
        type=int, 
        nargs="?", 
        default=1, 
        help="Number of nodes (default: 1, minimum: 1)"
    )
    args = parser.parse_args()

    if args.nodes < 1:
        print("Error: Node count must be at least 1")
        exit(1)

    generate(args.nodes)